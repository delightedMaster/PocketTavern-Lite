package me.rerere.rikkahub

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.rerere.rikkahub.ui.components.message.HTML_CARD_SOURCE_TAG
import me.rerere.rikkahub.ui.components.message.HTML_CARD_TILE_TAG
import me.rerere.rikkahub.ui.components.message.HtmlCardView
import me.rerere.rikkahub.ui.components.message.LocalTavernCardBridge
import me.rerere.rikkahub.ui.components.message.TavernCardBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Runs on a device or emulator. Slice math tests do not cover this: these checks load a real WebView.
 */
@RunWith(AndroidJUnit4::class)
class HtmlCardSliceDeviceTest {
    @Test
    fun slicesAreOneDocumentAndTheBottomBandIsPainted() {
        val scenario = ActivityScenario.launch(HtmlCardProbeActivity::class.java)
        show(scenario, bandDocument(BANDS, BAND))
        val web = waitFor<WebView>(scenario) { findTagged(it, HTML_CARD_SOURCE_TAG).filterIsInstance<WebView>() }
        val boots = waitText(scenario, web, "String(window.__boots||0)", "\"1\"")
        assertEquals("script ran once", "\"1\"", boots)
        assertEquals(1, countTagged(scenario, HTML_CARD_SOURCE_TAG))
        val tiles = waitTagged(scenario, HTML_CARD_TILE_TAG, minHeight = 400, minCount = 2)
        assertTrue("expected several slices, got ${tiles.size}", tiles.size >= 2)
        val density = ui(scenario) { web.resources.displayMetrics.density }
        val origin = location(scenario, tiles.minBy { location(scenario, it)[1] })
        for (band in listOf(0, BANDS / 2, BANDS - 1)) {
            val deviceY = ((band * BAND + BAND / 2) * density).roundToInt()
            val tile = tiles.firstOrNull { view ->
                val top = location(scenario, view)[1] - origin[1]
                deviceY in top until top + view.height
            } ?: error("no slice covers band $band at y=$deviceY")
            val localY = deviceY - (location(scenario, tile)[1] - origin[1])
            val color = ui(scenario) { drawPixel(tile, localY) }
            assertTrue(
                "band $band pixel ${hex(color)} expected ${bandHex(band)}",
                close(color, bandHex(band)),
            )
        }
        val visible = tiles.minBy { location(scenario, it)[1] }
        val copied = pixelCopy(scenario, visible, 48)
        assertTrue("on-screen slice was ${hex(copied)}", close(copied, bandHex(0)))
        scenario.close()
    }

    @Test
    fun buttonOverflowListScrollAndHeightStayOnOneDocument() {
        val scenario = ActivityScenario.launch(HtmlCardProbeActivity::class.java)
        val scroll = intArrayOf(0)
        val states = arrayOfNulls<ScrollState>(1)
        show(scenario, INTERACTIVE, states) { scroll[0] = it }
        val web = waitFor<WebView>(scenario) { findTagged(it, HTML_CARD_SOURCE_TAG).filterIsInstance<WebView>() }
        waitTagged(scenario, HTML_CARD_TILE_TAG, minHeight = 800, minCount = 1)
        val compose = ui(scenario) { it.root.getChildAt(0) }
        val tileTop = location(scenario, findTagged(scenario) { it.tag == HTML_CARD_TILE_TAG }.minBy { location(scenario, it)[1] })
        val composeTop = location(scenario, compose)
        val density = ui(scenario) { web.resources.displayMetrics.density }
        fun documentY(css: Float): Float = tileTop[1] - composeTop[1] + css * density

        tap(scenario, compose, x = 40f, y = documentY(30f))
        Thread.sleep(250)
        val clicks = eval(scenario, web, "String(window.__clicks||0)")
        val mark = eval(scenario, web, "getComputedStyle(document.getElementById('mark')).backgroundColor")
        assertEquals("\"1\"", clicks)
        assertTrue("mark=$mark", mark.contains("255") && mark.contains("0"))

        Thread.sleep(400)
        val beforeBox = eval(scenario, web, "document.getElementById('box').scrollTop").trim('"').toFloat()
        drag(scenario, compose, x = 40f, fromY = documentY(250f), toY = documentY(110f))
        Thread.sleep(200)
        val afterBox = eval(scenario, web, "document.getElementById('box').scrollTop").trim('"').toFloat()
        assertTrue("overflow scrollTop $beforeBox -> $afterBox, list=${scroll[0]}", afterBox > beforeBox + 10f)
        assertTrue("list moved during overflow drag: ${scroll[0]}", scroll[0] < 30)

        val beforeList = scroll[0]
        drag(scenario, compose, x = 40f, fromY = documentY(450f), toY = documentY(320f))
        Thread.sleep(200)
        val direct = ui(scenario) { states[0]?.value ?: -1 }
        assertTrue("list scroll $beforeList -> $direct", direct > beforeList + 20)

        show(scenario, "<style>html,body{margin:0}body{min-height:100vh}</style><div style='height:80px;background:#abc'></div>")
        waitTagged(scenario, HTML_CARD_TILE_TAG, minHeight = 40, minCount = 1)
        Thread.sleep(500)
        val heightA = tileHeight(scenario)
        Thread.sleep(400)
        val heightB = tileHeight(scenario)
        assertTrue("vh card grew $heightA -> $heightB", abs(heightA - heightB) <= 8)
        assertTrue("vh card is $heightB px", heightB in 40..500)
        show(scenario, "<div id=\"spin\" style=\"transform:rotate(8deg);height:40px\">x</div>")
        val transformWeb = waitForWeb(scenario, "String(!!document.getElementById('spin'))", "\"true\"")
        val rotated = eval(scenario, transformWeb, "getComputedStyle(document.getElementById('spin')).transform")
        val body = eval(scenario, transformWeb, "getComputedStyle(document.body).transform")
        assertTrue("author transform=$rotated", rotated.contains("0.139") || rotated.contains("0.990"))
        assertTrue("body transform was replaced: $body", !body.contains("matrix(1, 0, 0, 1, 0,"))
        scenario.close()
    }

    private fun show(
        scenario: ActivityScenario<HtmlCardProbeActivity>,
        html: String,
        states: Array<ScrollState?>? = null,
        onScroll: (Int) -> Unit = {},
    ) {
        ui(scenario) { activity ->
            activity.root.removeAllViews()
            val compose = ComposeView(activity).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    val state = rememberScrollState()
                    SideEffect {
                        states?.set(0, state)
                        onScroll(state.value)
                    }
                    MaterialTheme {
                        CompositionLocalProvider(
                            LocalTavernCardBridge provides TavernCardBridge { _, _ -> """{"result":null}""" },
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .height(520.dp)
                                    .verticalScroll(state),
                            ) {
                                HtmlCardView(messageId = "card", html = html)
                                Box(Modifier.height(80.dp))
                            }
                        }
                    }
                }
            }
            activity.root.addView(compose, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun bandDocument(bands: Int, band: Int): String = buildString {
        append("<style>html,body{margin:0;padding:0}</style>")
        append("<script>window.__boots=(window.__boots||0)+1</script>")
        for (i in 0 until bands) append("<div style=\"height:${band}px;background:${bandHex(i)}\"></div>")
    }

    private fun bandHex(i: Int): String = "#%02X%02X%02X".format((i * 37) and 255, (80 + i * 17) and 255, (20 + i * 53) and 255)

    private fun drawPixel(view: View, localY: Int): Int {
        val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val color = bitmap.getPixel(view.width / 2, localY.coerceIn(0, bitmap.height - 1))
        bitmap.recycle()
        return color
    }

    private fun pixelCopy(scenario: ActivityScenario<HtmlCardProbeActivity>, view: View, height: Int): Int {
        val latch = CountDownLatch(1)
        val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), height, Bitmap.Config.ARGB_8888)
        var code = PixelCopy.ERROR_SOURCE_INVALID
        ui(scenario) { activity ->
            val loc = IntArray(2)
            view.getLocationInWindow(loc)
            val rect = Rect(loc[0], loc[1], loc[0] + view.width, loc[1] + height)
            PixelCopy.request(activity.window, rect, bitmap, { result ->
                code = result
                latch.countDown()
            }, Handler(Looper.getMainLooper()))
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        val color = bitmap.getPixel(view.width / 2, height / 2)
        bitmap.recycle()
        assertEquals(PixelCopy.SUCCESS, code)
        return color
    }

    private fun tap(scenario: ActivityScenario<HtmlCardProbeActivity>, target: View, x: Float, y: Float) {
        ui(scenario) {
            val down = SystemClock.uptimeMillis()
            val press = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
            val up = MotionEvent.obtain(down, down + 50, MotionEvent.ACTION_UP, x, y, 0).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
            target.dispatchTouchEvent(press)
            target.dispatchTouchEvent(up)
            press.recycle()
            up.recycle()
        }
    }

    private fun drag(
        scenario: ActivityScenario<HtmlCardProbeActivity>,
        target: View,
        x: Float,
        fromY: Float,
        toY: Float,
    ) {
        ui(scenario) {
            val down = SystemClock.uptimeMillis()
            fun send(action: Int, y: Float, time: Long) {
                val event = MotionEvent.obtain(down, time, action, x, y, 0).apply {
                    source = InputDevice.SOURCE_TOUCHSCREEN
                }
                target.dispatchTouchEvent(event)
                event.recycle()
            }
            send(MotionEvent.ACTION_DOWN, fromY, down)
            val steps = 8
            for (step in 1..steps) {
                send(MotionEvent.ACTION_MOVE, fromY + (toY - fromY) * step / steps, down + step * 16L)
            }
            send(MotionEvent.ACTION_UP, toY, down + 200)
        }
    }

    private fun tileHeight(scenario: ActivityScenario<HtmlCardProbeActivity>): Int =
        ui(scenario) { activity ->
            findTagged(activity, HTML_CARD_TILE_TAG).sumOf { it.height }
        }

    private fun countTagged(scenario: ActivityScenario<HtmlCardProbeActivity>, tag: String): Int =
        ui(scenario) { findTagged(it, tag).size }

    private fun findTagged(scenario: ActivityScenario<HtmlCardProbeActivity>, predicate: (View) -> Boolean): List<View> =
        ui(scenario) { activity ->
            val found = ArrayList<View>()
            fun walk(view: View) {
                if (predicate(view)) found.add(view)
                if (view is ViewGroup) {
                    for (index in 0 until view.childCount) walk(view.getChildAt(index))
                }
            }
            walk(activity.window.decorView)
            found
        }

    private fun findTagged(activity: HtmlCardProbeActivity, tag: String): List<View> {
        val found = ArrayList<View>()
        fun walk(view: View) {
            if (view.tag == tag) found.add(view)
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) walk(view.getChildAt(index))
            }
        }
        walk(activity.window.decorView)
        return found
    }

    private fun waitTagged(
        scenario: ActivityScenario<HtmlCardProbeActivity>,
        tag: String,
        minHeight: Int,
        minCount: Int,
    ): List<View> {
        val deadline = SystemClock.uptimeMillis() + 12_000
        while (SystemClock.uptimeMillis() < deadline) {
            val found = ui(scenario) {
                findTagged(it, tag).filter { view -> view.isAttachedToWindow && view.width > 0 && view.height >= minHeight }
            }
            if (found.size >= minCount) return found
            Thread.sleep(40)
        }
        error("not enough views tagged $tag")
    }

    private fun waitForWeb(
        scenario: ActivityScenario<HtmlCardProbeActivity>,
        js: String,
        expected: String,
    ): WebView {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            val webs = ui(scenario) { findTagged(it, HTML_CARD_SOURCE_TAG).filterIsInstance<WebView>() }
            for (web in webs) {
                val value = runCatching { eval(scenario, web, js) }.getOrNull()
                if (value == expected) return web
            }
            Thread.sleep(40)
        }
        error("no webview reached $expected for $js")
    }

    private fun <T : View> waitFor(
        scenario: ActivityScenario<HtmlCardProbeActivity>,
        probe: (HtmlCardProbeActivity) -> List<T>,
    ): T {
        val deadline = SystemClock.uptimeMillis() + 12_000
        while (SystemClock.uptimeMillis() < deadline) {
            val found = ui(scenario) { probe(it).firstOrNull { view -> view.isAttachedToWindow } }
            if (found != null) return found
            Thread.sleep(40)
        }
        error("view did not appear")
    }

    private fun waitText(
        scenario: ActivityScenario<HtmlCardProbeActivity>,
        web: WebView,
        js: String,
        expected: String,
    ): String {
        val deadline = SystemClock.uptimeMillis() + 8_000
        var latest = "timeout"
        while (SystemClock.uptimeMillis() < deadline) {
            latest = eval(scenario, web, js)
            if (latest == expected) return latest
            Thread.sleep(40)
        }
        return latest
    }

    private fun eval(scenario: ActivityScenario<HtmlCardProbeActivity>, web: WebView, js: String): String {
        val latch = CountDownLatch(1)
        val box = arrayOf("timeout")
        ui(scenario) {
            web.post {
                web.evaluateJavascript(js) { value ->
                    box[0] = value ?: "null"
                    latch.countDown()
                }
            }
        }
        assertTrue(latch.await(8, TimeUnit.SECONDS))
        return box[0]
    }

    private fun location(scenario: ActivityScenario<HtmlCardProbeActivity>, view: View): IntArray =
        ui(scenario) {
            val out = IntArray(2)
            view.getLocationInWindow(out)
            out
        }

    private fun <T> ui(scenario: ActivityScenario<HtmlCardProbeActivity>, block: (HtmlCardProbeActivity) -> T): T {
        val latch = CountDownLatch(1)
        val box = arrayOfNulls<Any>(1)
        var failure: Throwable? = null
        scenario.onActivity { activity ->
            try {
                box[0] = block(activity)
            } catch (error: Throwable) {
                failure = error
            } finally {
                latch.countDown()
            }
        }
        if (!latch.await(15, TimeUnit.SECONDS)) error("ui timeout")
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return box[0] as T
    }

    private fun hex(color: Int): String = "#%08X".format(color)

    private fun close(actual: Int, expectedHex: String): Boolean {
        val expected = Color.parseColor(expectedHex)
        return abs(Color.red(actual) - Color.red(expected)) <= 16 &&
            abs(Color.green(actual) - Color.green(expected)) <= 16 &&
            abs(Color.blue(actual) - Color.blue(expected)) <= 16
    }

    private companion object {
        const val BAND = 1000
        const val BANDS = 8
        val INTERACTIVE = """
            <style>html,body{margin:0;padding:0}</style>
            <button id="btn" style="display:block;width:100%;height:70px" onclick="window.__clicks=(window.__clicks||0)+1;document.getElementById('mark').style.background='#ff0000'">go</button>
            <div id="mark" style="height:20px;background:#112233"></div>
            <div id="box" style="height:200px;overflow:auto"><div style="height:1600px;background:#2266cc"></div></div>
            <div id="pad" style="height:1800px;background:#89abcd"></div>
        """.trimIndent()
    }
}
