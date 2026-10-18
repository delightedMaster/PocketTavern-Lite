package me.rerere.rikkahub.ui.components.message

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.ai.cards.cardDocument
import me.rerere.rikkahub.data.ai.cards.cardRequestAllowed
import java.io.ByteArrayInputStream

fun interface TavernCardBridge {
    fun handle(messageId: String, requestJson: String): String
}

val LocalTavernCardBridge = staticCompositionLocalOf<TavernCardBridge?> { null }
val LocalHtmlCardAllowlist = compositionLocalOf { emptyList<String>() }

/**
 * One WebView per message. JavaScript can only call [TavernCardBridge].
 * Network is blocked unless the host is on the allowlist. DOM storage stays off,
 * so a card cannot keep its own copy of app data.
 */
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
    val document = remember(html) { cardDocument(html) }
    val scope = rememberCoroutineScope()
    var heightPx by remember(messageId) { mutableIntStateOf(48) }
    val height = heightPx.coerceIn(32, 720).dp
    if (bridge == null) return
    AndroidView(
        modifier = modifier.fillMaxWidth().height(height),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
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
                    onHeight = { px -> scope.launch { heightPx = px } },
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
                }
                tag = document
                loadDataWithBaseURL(null, document, "text/html", "utf-8", null)
            }
        },
        update = { webView ->
            webView.settings.blockNetworkLoads = allowlist.isEmpty()
            if (webView.tag != document) {
                webView.tag = document
                webView.loadDataWithBaseURL(null, document, "text/html", "utf-8", null)
            }
        },
        onRelease = { webView ->
            webView.removeJavascriptInterface("PocketTavern")
            webView.destroy()
        },
    )
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
