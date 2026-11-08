package me.rerere.rikkahub.ui.components.message

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import me.rerere.rikkahub.data.ai.cards.cardDocument
import me.rerere.rikkahub.data.ai.cards.cardRequestAllowed
import me.rerere.rikkahub.data.ai.cards.devicePointInScrollable
import me.rerere.rikkahub.data.ai.cards.htmlCardMaxSliceCssPx
import me.rerere.rikkahub.data.ai.cards.htmlCardSlices
import me.rerere.rikkahub.data.ai.cards.parseScrollableRects
import me.rerere.rikkahub.data.ai.cards.shouldApplyCardHeight
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import java.io.ByteArrayInputStream
import kotlin.math.roundToInt

internal const val HTML_CARD_TILE_TAG = "html-card-tile"
internal const val HTML_CARD_SOURCE_TAG = "html-card-source"

fun interface TavernCardBridge {
    fun handle(messageId: String, requestJson: String): String
}

val LocalTavernCardBridge = staticCompositionLocalOf<TavernCardBridge?> { null }
val LocalHtmlCardAllowlist = compositionLocalOf { emptyList<String>() }

/**
 * One WebView owns the document, so scripts, timers and button state run once.
 * The chat list is the only scroller for the reply itself. Slices are bitmaps of that
 * same document, each shorter than the GPU texture limit. A region the author marked
 * `overflow: auto` still scrolls inside the document; other vertical drags go to the list.
 */
@Suppress("DEPRECATION")
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlCardView(
    messageId: String,
    html: String,
    modifier: Modifier = Modifier,
) {
    val bridge = LocalTavernCardBridge.current ?: return
    val allowlist = LocalHtmlCardAllowlist.current
    val bridgeState = rememberUpdatedState(bridge)
    val allowlistState = rememberUpdatedState(allowlist)
    val dark = LocalDarkMode.current
    val textColor = MaterialTheme.colorScheme.onSurface
    val textCss = remember(textColor) {
        "#%02X%02X%02X".format(
            (textColor.red * 255).toInt().coerceIn(0, 255),
            (textColor.green * 255).toInt().coerceIn(0, 255),
            (textColor.blue * 255).toInt().coerceIn(0, 255),
        )
    }
    val document = remember(html, textCss, dark) { cardDocument(html, textColor = textCss, dark = dark) }
    val density = LocalDensity.current.density
    val context = LocalContext.current
    val anchor = LocalView.current
    val maxSliceCssPx = remember(density) {
        htmlCardMaxSliceCssPx(Canvas().maximumBitmapHeight, glMaxTextureSize(), density)
    }
    var contentCssPx by remember(document) { mutableIntStateOf(32) }
    val picture = remember(document, messageId) {
        CardPicture(
            context = context,
            document = document,
            density = density,
            onHeight = {},
            onHandle = { request ->
                bridgeState.value.handle(messageId, request)
            },
        )
    }
    SideEffect {
        picture.onHeight = { reported ->
            if (shouldApplyCardHeight(contentCssPx, reported)) contentCssPx = reported
            picture.markDirty()
        }
        picture.appliedCss = contentCssPx
        picture.allowlist = allowlistState.value
    }
    DisposableEffect(picture) {
        picture.attach(anchor)
        onDispose { picture.destroy() }
    }
    val slices = htmlCardSlices(contentCssPx, maxSliceCssPx)
    Column(modifier = modifier.fillMaxWidth()) {
        slices.forEach { slice ->
            key(slice.offsetCssPx) {
                val offsetPx = (slice.offsetCssPx * density).roundToInt()
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(slice.heightCssPx.dp)
                        .pointerInput(picture, offsetPx) {
                            detectTapGestures { offset ->
                                picture.click(offset.x, offsetPx + offset.y)
                            }
                        },
                    factory = { viewContext ->
                        CardTileView(viewContext, picture, offsetPx).apply { tag = HTML_CARD_TILE_TAG }
                    },
                    update = { tile ->
                        tile.offsetPx = offsetPx
                        tile.invalidate()
                    },
                )
            }
        }
    }
}

private class Raster(val bitmap: Bitmap, val generation: Int)

/**
 * The single document for a card. It is attached so WebView will paint, but its host
 * does not draw it into the window: the slices do, via [raster].
 */
@Suppress("DEPRECATION")
@SuppressLint("SetJavaScriptEnabled")
private class CardPicture(
    context: Context,
    document: String,
    private val density: Float,
    var onHeight: (Int) -> Unit,
    private val onHandle: (String) -> String,
) {
    private val host = object : FrameLayout(context) {
        override fun dispatchDraw(canvas: Canvas) = Unit
        override fun requestRectangleOnScreen(rectangle: Rect, immediate: Boolean) = false
    }
    private val web: CardWebView = CardWebView(context)
    private val bitmaps = HashMap<Int, Raster>()
    private val tiles = ArrayList<View>()
    private var generation = 0
    private var dirtyPosted = false
    private var scrollableCss = IntArray(0)
    private var destroyed = false
    var appliedCss: Int = 32
    var allowlist: List<String> = emptyList()
        set(value) {
            field = value
            web.settings.blockNetworkLoads = value.isEmpty()
        }

    init {
        host.clipChildren = true
        web.tag = HTML_CARD_SOURCE_TAG
        web.setBackgroundColor(Color.TRANSPARENT)
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false
        web.overScrollMode = View.OVER_SCROLL_NEVER
        web.settings.javaScriptEnabled = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) web.settings.forceDark = WebSettings.FORCE_DARK_OFF
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) web.settings.isAlgorithmicDarkeningAllowed = false
        web.settings.domStorageEnabled = false
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        @Suppress("DEPRECATION")
        web.settings.allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        web.settings.allowUniversalAccessFromFileURLs = false
        web.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        web.settings.blockNetworkLoads = true
        web.addJavascriptInterface(CardJsBridge(this), "PocketTavern")
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (cardRequestAllowed(request.url.toString(), allowlist)) return null
                return WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    403,
                    "blocked",
                    emptyMap(),
                    ByteArrayInputStream(ByteArray(0)),
                )
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                markDirty()
            }
        }
        web.loadDataWithBaseURL(null, document, "text/html", "utf-8", null)
    }

    fun attach(anchor: View) {
        if (destroyed || host.parent != null) return
        val root = anchor.rootView as? ViewGroup ?: return
        root.addView(host, FrameLayout.LayoutParams(1, 1))
        host.addView(web, FrameLayout.LayoutParams(1, 1))
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        bitmaps.values.forEach { it.bitmap.recycle() }
        bitmaps.clear()
        tiles.clear()
        web.removeJavascriptInterface("PocketTavern")
        (host.parent as? ViewGroup)?.removeView(host)
        web.destroy()
    }

    fun markDirty() {
        if (destroyed || dirtyPosted) return
        dirtyPosted = true
        web.post {
            dirtyPosted = false
            if (destroyed) return@post
            generation += 1
            bitmaps.values.forEach { it.bitmap.recycle() }
            bitmaps.clear()
            tiles.forEach { it.postInvalidate() }
        }
    }

    fun watch(tile: View) {
        if (tile !in tiles) tiles.add(tile)
    }

    fun unwatch(tile: View) {
        tiles.remove(tile)
    }

    fun reportHeight(height: Int) {
        if (destroyed) return
        web.post { onHeight(height) }
    }

    fun reportScrollables(csv: String) {
        if (destroyed) return
        web.post { scrollableCss = parseScrollableRects(csv) }
    }

    fun handle(request: String): String = runCatching { onHandle(request) }
        .getOrElse { """{"error":"card bridge failed"}""" }

    fun scrollableAt(deviceX: Float, deviceY: Float): Boolean =
        devicePointInScrollable(deviceX, deviceY, scrollableCss, scale())

    fun click(deviceX: Float, deviceY: Float) {
        if (destroyed) return
        val scale = scale()
        val x = deviceX / scale
        val y = deviceY / scale
        web.evaluateJavascript(
            "(function(){var el=document.elementFromPoint($x,$y);if(el)el.click();})()",
            null,
        )
    }

    fun forward(event: MotionEvent, offsetPx: Int) {
        if (destroyed) return
        val copy = MotionEvent.obtain(event)
        copy.offsetLocation(0f, offsetPx.toFloat())
        web.dispatchTouchEvent(copy)
        copy.recycle()
    }

    fun raster(offsetPx: Int, width: Int, height: Int): Bitmap? {
        if (destroyed || width <= 0 || height <= 0) return null
        val cached = bitmaps[offsetPx]
        if (cached != null && cached.generation == generation && cached.bitmap.width == width && cached.bitmap.height == height) {
            return cached.bitmap
        }
        cached?.bitmap?.recycle()
        ensureLayout(width)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate(0f, -offsetPx.toFloat())
        web.draw(canvas)
        bitmaps[offsetPx] = Raster(bitmap, generation)
        return bitmap
    }

    fun release(offsetPx: Int) {
        bitmaps.remove(offsetPx)?.bitmap?.recycle()
    }

    private fun scale(): Float {
        val reported = web.scale
        // Before the viewport meta is applied, WebView reports 1 even on a dense screen.
        return if (reported > 1.1f) reported else density.coerceAtLeast(0.1f)
    }

    private fun ensureLayout(width: Int) {
        val css = maxOf(web.contentHeight, appliedCss).coerceAtLeast(1)
        val contentPx = (css * scale()).roundToInt().coerceAtLeast(1)
        if (web.width == width && web.height >= contentPx) return
        web.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(contentPx, View.MeasureSpec.EXACTLY),
        )
        web.layout(0, 0, width, contentPx)
    }
}

private class CardWebView(context: Context) : WebView(context) {
    private var lockingScroll = false

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (!lockingScroll && scrollY != 0) {
            lockingScroll = true
            scrollTo(scrollX, 0)
            lockingScroll = false
        }
    }

    override fun requestRectangleOnScreen(rectangle: Rect, immediate: Boolean): Boolean = false
}

private class CardTileView(
    context: Context,
    private val picture: CardPicture,
    var offsetPx: Int,
) : View(context) {
    private var trackingInner = false

    init {
        picture.watch(this)
    }

    override fun onDetachedFromWindow() {
        picture.unwatch(this)
        picture.release(offsetPx)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val bitmap = picture.raster(offsetPx, width, height) ?: return
        canvas.drawBitmap(bitmap, 0f, 0f, null)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (!isVisible) picture.release(offsetPx)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                trackingInner = picture.scrollableAt(event.x, offsetPx + event.y)
                if (!trackingInner) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                picture.forward(event, offsetPx)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!trackingInner) return false
                picture.forward(event, offsetPx)
                picture.markDirty()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!trackingInner) return false
                picture.forward(event, offsetPx)
                picture.markDirty()
                trackingInner = false
            }
        }
        return trackingInner
    }
}

private class CardJsBridge(private val picture: CardPicture) {
    @JavascriptInterface
    fun handle(request: String): String = picture.handle(request)

    @JavascriptInterface
    fun reportHeight(height: Int) {
        picture.reportHeight(height)
    }

    @JavascriptInterface
    fun reportScrollables(csv: String) {
        picture.reportScrollables(csv)
    }
}

private fun glMaxTextureSize(): Int {
    val cached = GlTextureLimit.value
    if (cached != GlTextureLimit.UNKNOWN) return cached
    val measured = runCatching { queryGlMaxTextureSize() }.getOrDefault(0)
    GlTextureLimit.value = measured
    return measured
}

private fun queryGlMaxTextureSize(): Int {
    val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    if (display == EGL14.EGL_NO_DISPLAY) return 0
    val version = IntArray(2)
    if (!EGL14.eglInitialize(display, version, 0, version, 1)) return 0
    return try {
        val attribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) || count[0] == 0) return 0
        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, 16, EGL14.EGL_HEIGHT, 16, EGL14.EGL_NONE)
        val surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfaceAttribs, 0)
        EGL14.eglMakeCurrent(display, surface, surface, context)
        val out = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, out, 0)
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, context)
        out[0]
    } finally {
        EGL14.eglTerminate(display)
    }
}

private object GlTextureLimit {
    const val UNKNOWN = -1
    var value: Int = UNKNOWN
}
