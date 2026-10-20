package me.rerere.rikkahub.data.ai.cards

import org.junit.Assert.*
import org.junit.Test

class CardAutoRenderTest {
    @Test
    fun `closed HTML fences render automatically including simple fragments`() {
        assertTrue(shouldRenderCardCode("html", "<button>选择</button>", true))
        assertTrue(shouldRenderCardCode("HTML", "<html><body>ok</body></html>", true))
        assertTrue(shouldRenderCardCode(null, "<div>状态栏</div>", true))
    }

    @Test
    fun `unfinished fences and HTML strings in other languages stay as source code`() {
        assertFalse(shouldRenderCardCode("html", "<html><script>", false))
        assertFalse(shouldRenderCardCode("python", "print('<div>hi</div>')", true))
        assertFalse(shouldRenderCardCode(null, "plain text", true))
    }

    @Test
    fun `fenced documents and mixed prose stay in Markdown for per block rendering`() {
        assertFalse(isStandaloneCardHtml("```html\n<div>ok</div>\n```"))
        assertFalse(isStandaloneCardHtml("正文\n```html\n<div>ok</div>\n```\n后文"))
        assertTrue(isStandaloneCardHtml("  <!DOCTYPE html><html><body>ok</body></html>"))
        assertTrue(isStandaloneCardHtml("<div>ok</div>"))
    }
}
