package me.rerere.rikkahub.ui.components.message

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
import me.rerere.rikkahub.data.ai.cards.CARD_SCROLL_VERTICAL
import me.rerere.rikkahub.data.ai.cards.cardDocument
import me.rerere.rikkahub.data.ai.cards.cardRequestAllowed
import me.rerere.rikkahub.data.ai.cards.parseScrollableRects
import me.rerere.rikkahub.data.ai.cards.scrollAxesAt
import me.rerere.rikkahub.data.ai.cards.shouldApplyCardHeight
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import java.io.ByteArrayInputStream
import kotlin.math.abs

internal const val HTML_CARD_SOURCE_TAG = "html-card-source"

fun interface TavernCardBridge {
    fun handle(messageId: String, requestJson: String): String
}

val LocalTavernCardBridge = staticCompositionLocalOf<TavernCardBridge?> { null }
val LocalHtmlCardAllowlist = compositionLocalOf { emptyList<String>() }

/**
 * One live WebView, as tall as the document. The reply itself does not scroll vertically;
 * a vertical drag on ordinary text goes to the chat list. Overflow regions, horizontal
 * movement, taps, and long-presses stay in the WebView, so selection, links, forms and
 * canvas animation keep the platform WebView behavior.
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
    var contentCssPx by remember(document) { mutableIntStateOf(32) }
    AndroidView(
        modifier = modifier.fillMaxWidth().height(contentCssPx.dp),
        factory = { context ->
            CardWebView(context, density).apply {
                tag = HTML_CARD_SOURCE_TAG
                loadDocument(document)
            }
        },
        update = { web ->
            web.onHeight = { reported ->
                if (shouldApplyCardHeight(contentCssPx, reported)) contentCssPx = reported
            }
            web.onHandle = { request -> bridgeState.value.handle(messageId, request) }
            web.allowlist = allowlistState.value
            if (web.loadedDocument != document) web.loadDocument(document)
        },
        onRelease = { web -> web.release() },
    )
}

@Suppress("DEPRECATION")
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
private class CardWebView(
    context: Context,
    private val density: Float,
) : WebView(context), NestedScrollingChild3 {
    private val helper = NestedScrollingChildHelper(this)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var scrollableCss = IntArray(0)
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragged = false
    private var mode = MODE_UNDECIDED
    private var lockingScroll = false
    var loadedDocument: String? = null
    var onHeight: (Int) -> Unit = {}
    var onHandle: (String) -> String = { """{"error":"card bridge missing"}""" }
    var allowlist: List<String> = emptyList()
        set(value) {
            field = value
            settings.blockNetworkLoads = value.isEmpty()
        }

    init {
        isNestedScrollingEnabled = true
        setBackgroundColor(Color.TRANSPARENT)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = true
        overScrollMode = OVER_SCROLL_NEVER
        isFocusable = true
        isFocusableInTouchMode = true
        settings.javaScriptEnabled = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) settings.forceDark = WebSettings.FORCE_DARK_OFF
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) settings.isAlgorithmicDarkeningAllowed = false
        settings.domStorageEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        settings.allowUniversalAccessFromFileURLs = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.blockNetworkLoads = true
        addJavascriptInterface(CardJsBridge(this), "PocketTavern")
        webViewClient = object : WebViewClient() {
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
        }
    }

    fun loadDocument(document: String) {
        loadedDocument = document
        loadDataWithBaseURL(null, document, "text/html", "utf-8", null)
    }

    fun release() {
        removeJavascriptInterface("PocketTavern")
        destroy()
    }

    fun reportHeight(height: Int) {
        post { onHeight(height) }
    }

    fun reportScrollables(csv: String) {
        post { scrollableCss = parseScrollableRects(csv) }
    }

    fun handle(request: String): String = runCatching { onHandle(request) }
        .getOrElse { """{"error":"card bridge failed"}""" }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        // The view is as tall as the document, so a vertical scroll would be a second
        // scroller for the whole reply. Horizontal position is the page's own.
        if (!lockingScroll && scrollY != 0) {
            lockingScroll = true
            scrollTo(scrollX, 0)
            lockingScroll = false
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastY = event.y
                dragged = false
                mode = MODE_UNDECIDED
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
                super.onTouchEvent(event)
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.x - downX)
                val dy = abs(event.y - downY)
                if (!dragged && (dx > touchSlop || dy > touchSlop)) {
                    dragged = true
                    val vertical = dy >= dx
                    val axes = scrollAxesAt(downX, downY, scrollableCss, cssScale())
                    mode = when {
                        vertical && axes and CARD_SCROLL_VERTICAL != 0 -> MODE_INNER
                        !vertical -> MODE_HORIZONTAL
                        else -> MODE_PARENT
                    }
                    if (mode != MODE_PARENT) parent?.requestDisallowInterceptTouchEvent(true)
                    if (mode == MODE_PARENT) {
                        val cancel = MotionEvent.obtain(event)
                        cancel.action = MotionEvent.ACTION_CANCEL
                        super.onTouchEvent(cancel)
                        cancel.recycle()
                    }
                }
                if (mode == MODE_PARENT && dragged) {
                    val delta = (lastY - event.y).toInt()
                    val used = IntArray(2)
                    dispatchNestedPreScroll(0, delta, used, null, ViewCompat.TYPE_TOUCH)
                    dispatchNestedScroll(0, 0, 0, delta - used[1], null, ViewCompat.TYPE_TOUCH)
                } else {
                    super.onTouchEvent(event)
                }
                lastY = event.y
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!(mode == MODE_PARENT && dragged)) super.onTouchEvent(event)
                stopNestedScroll(ViewCompat.TYPE_TOUCH)
                dragged = false
                mode = MODE_UNDECIDED
            }
        }
        return true
    }

    private fun cssScale(): Float {
        @Suppress("DEPRECATION")
        val reported = scale
        return if (reported > 1.1f) reported else density.coerceAtLeast(0.1f)
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

    override fun dispatchNestedPreScroll(dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?): Boolean =
        helper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow)

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
        type: Int,
    ): Boolean = helper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)

    private companion object {
        const val MODE_UNDECIDED = 0
        const val MODE_PARENT = 1
        const val MODE_INNER = 2
        const val MODE_HORIZONTAL = 3
    }
}

private class CardJsBridge(private val web: CardWebView) {
    @JavascriptInterface
    fun handle(request: String): String = web.handle(request)

    @JavascriptInterface
    fun reportHeight(height: Int) {
        web.reportHeight(height)
    }

    @JavascriptInterface
    fun reportScrollables(csv: String) {
        web.reportScrollables(csv)
    }
}
