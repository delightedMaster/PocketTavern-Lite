package me.rerere.rikkahub.ui.components.message

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.ai.cards.cardDocument
import me.rerere.rikkahub.data.ai.cards.cardRequestAllowed
import me.rerere.rikkahub.data.ai.cards.htmlCardMaxSliceCssPx
import me.rerere.rikkahub.data.ai.cards.htmlCardSlices
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import java.io.ByteArrayInputStream
import kotlin.math.abs

fun interface TavernCardBridge {
    fun handle(messageId: String, requestJson: String): String
}

val LocalTavernCardBridge = staticCompositionLocalOf<TavernCardBridge?> { null }
val LocalHtmlCardAllowlist = compositionLocalOf { emptyList<String>() }

/**
 * The card grows to its content and the chat list is the only vertical scroller.
 * A single WebView taller than [Canvas.getMaximumBitmapHeight] is split into stacked
 * pieces. Those pieces are not scrolled by the user.
 */
@Suppress("DEPRECATION")
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlCardView(
    messageId: String,
    html: String,
    modifier: Modifier = Modifier,
) {
    val bridge = LocalTavernCardBridge.current
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
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val maxSliceCssPx = remember(density) {
        htmlCardMaxSliceCssPx(Canvas().maximumBitmapHeight, density)
    }
    var contentCssPx by remember(messageId) { mutableIntStateOf(32) }
    val slices = htmlCardSlices(contentCssPx, maxSliceCssPx)
    if (bridge == null) return
    Column(modifier = modifier.fillMaxWidth()) {
        slices.forEach { slice ->
            key(slice.offsetCssPx) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(slice.heightCssPx.dp),
                    factory = { context ->
                        CardWebView(context).apply {
                            setBackgroundColor(Color.TRANSPARENT)
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = true
                            overScrollMode = View.OVER_SCROLL_NEVER
                            settings.javaScriptEnabled = true
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                settings.forceDark = WebSettings.FORCE_DARK_OFF
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                settings.isAlgorithmicDarkeningAllowed = false
                            }
                            settings.domStorageEnabled = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            @Suppress("DEPRECATION")
                            settings.allowFileAccessFromFileURLs = false
                            @Suppress("DEPRECATION")
                            settings.allowUniversalAccessFromFileURLs = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.blockNetworkLoads = allowlistState.value.isEmpty()
                            val host = CardJsBridge(
                                onHandle = { request ->
                                    bridgeState.value?.handle(messageId, request)
                                        ?: """{"error":"card bridge missing"}"""
                                },
                                onHeight = { px ->
                                    if (px > 0) scope.launch { contentCssPx = px }
                                },
                            )
                            addJavascriptInterface(host, "PocketTavern")
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): WebResourceResponse? {
                                    if (cardRequestAllowed(request.url.toString(), allowlistState.value)) return null
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
                                    (view as? CardWebView)?.onPageReady()
                                }
                            }
                            tag = document
                            loadDataWithBaseURL(null, document, "text/html", "utf-8", null)
                        }
                    },
                    update = { webView ->
                        webView.settings.blockNetworkLoads = allowlist.isEmpty()
                        if (webView.tag != document) {
                            webView.tag = document
                            webView.markReloading()
                            webView.loadDataWithBaseURL(null, document, "text/html", "utf-8", null)
                        }
                        webView.setSliceOffset(slice.offsetCssPx)
                    },
                    onRelease = { webView ->
                        webView.removeJavascriptInterface("PocketTavern")
                        webView.destroy()
                    },
                )
            }
        }
    }
}

/**
 * Shows one vertical window of the document and gives vertical drags to the parent list.
 * Horizontal moves and taps stay in the WebView, so an author-made inner scroller and
 * buttons still work. The document is shifted with a transform; this view does not scroll.
 */
private class CardWebView(context: Context) : WebView(context), NestedScrollingChild3 {
    private val helper = NestedScrollingChildHelper(this)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var draggingVertically = false
    private var draggingHorizontally = false
    private var sliceOffsetCss = 0
    private var pageReady = false
    private var lockingScroll = false

    init {
        isNestedScrollingEnabled = true
    }

    fun markReloading() {
        pageReady = false
    }

    fun setSliceOffset(offsetCssPx: Int) {
        if (sliceOffsetCss == offsetCssPx && pageReady) return
        sliceOffsetCss = offsetCssPx
        if (pageReady) pushSlice()
    }

    fun onPageReady() {
        pageReady = true
        pushSlice()
    }

    private fun pushSlice() {
        val offset = sliceOffsetCss
        val shift = if (offset == 0) "" else "translateY(-${offset}px)"
        evaluateJavascript(
            "document.body&&(document.body.style.transform='$shift')",
            null,
        )
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (!lockingScroll && scrollY != 0) {
            lockingScroll = true
            scrollTo(scrollX, 0)
            lockingScroll = false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastY = event.y
                draggingVertically = false
                draggingHorizontally = false
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
                super.onTouchEvent(event)
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.x - downX)
                val dy = abs(event.y - downY)
                if (!draggingVertically && !draggingHorizontally) {
                    if (dy > touchSlop && dy > dx) draggingVertically = true
                    else if (dx > touchSlop && dx > dy) draggingHorizontally = true
                }
                if (draggingVertically) {
                    val delta = (lastY - event.y).toInt()
                    lastY = event.y
                    val used = IntArray(2)
                    dispatchNestedPreScroll(0, delta, used, null, ViewCompat.TYPE_TOUCH)
                    dispatchNestedScroll(0, 0, 0, delta - used[1], null, ViewCompat.TYPE_TOUCH)
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
                lastY = event.y
                if (draggingHorizontally) parent?.requestDisallowInterceptTouchEvent(true)
                super.onTouchEvent(event)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                stopNestedScroll(ViewCompat.TYPE_TOUCH)
                if (draggingVertically) {
                    val cancel = MotionEvent.obtain(event)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.onTouchEvent(cancel)
                    cancel.recycle()
                } else {
                    super.onTouchEvent(event)
                }
                draggingVertically = false
                draggingHorizontally = false
            }
        }
        return true
    }

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        helper.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean = helper.isNestedScrollingEnabled

    override fun startNestedScroll(axes: Int): Boolean = helper.startNestedScroll(axes)

    override fun startNestedScroll(axes: Int, type: Int): Boolean = helper.startNestedScroll(axes, type)

    override fun stopNestedScroll() {
        helper.stopNestedScroll()
    }

    override fun stopNestedScroll(type: Int) {
        helper.stopNestedScroll(type)
    }

    override fun hasNestedScrollingParent(): Boolean = helper.hasNestedScrollingParent()

    override fun hasNestedScrollingParent(type: Int): Boolean = helper.hasNestedScrollingParent(type)

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
    ): Boolean = helper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow)

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int,
    ): Boolean = helper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type)

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int,
        consumed: IntArray,
    ) {
        helper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type, consumed)
    }

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
    ): Boolean = helper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow)

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
        type: Int,
    ): Boolean = helper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)
}

private class CardJsBridge(
    private val onHandle: (String) -> String,
    private val onHeight: (Int) -> Unit,
) {
    @JavascriptInterface
    fun handle(request: String): String = runCatching { onHandle(request) }
        .getOrElse { """{"error":"card bridge failed"}""" }

    @JavascriptInterface
    fun reportHeight(height: Int) {
        onHeight(height)
    }
}
