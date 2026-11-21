package me.rerere.ui.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownInlineTest {
    private val orange = Color(0xFFD25F00)
    private val gray = Color(0xFF6B7785)

    private val dialogueTheme = MarkdownTheme(
        headings = emptyList(),
        link = SpanStyle(color = Color.Blue),
        inlineCode = SpanStyle(color = Color.Green),
        blockSpacing = 12.dp,
        markerColor = Color.Blue,
        quoteColor = Color.Gray,
        dividerColor = Color.LightGray,
        quotedText = SpanStyle(color = orange),
        parenthesisText = SpanStyle(color = gray),
        hideParentheses = true,
    )

    private fun render(markdown: String, theme: MarkdownTheme = dialogueTheme): AnnotatedString {
        val block = parseMarkdown(markdown).blocks.single() as MarkdownBlock.Paragraph
        return buildInlineAnnotatedText(block.content, theme)
    }

    private fun AnnotatedString.colorOf(substring: String): Color {
        val start = text.indexOf(substring)
        assertTrue("Expected '$substring' in '$text'", start >= 0)
        val end = start + substring.length
        return spanStyles
            .filter { it.start <= start && it.end >= end && it.item.color != Color.Unspecified }
            .lastOrNull()
            ?.item
            ?.color
            ?: Color.Unspecified
    }

    @Test
    fun `colors Chinese and Japanese dialogue quotes in orange`() {
        val rendered = render("她说：“你好。”他又回：「早安。」以及『晚安』和\"Hello\"")
        assertEquals("她说：“你好。”他又回：「早安。」以及『晚安』和\"Hello\"", rendered.text)
        assertEquals(Color.Unspecified, rendered.colorOf("她说："))
        assertEquals(orange, rendered.colorOf("“你好。”"))
        assertEquals(Color.Unspecified, rendered.colorOf("他又回："))
        assertEquals(orange, rendered.colorOf("「早安。」"))
        assertEquals(orange, rendered.colorOf("『晚安』"))
        assertEquals(orange, rendered.colorOf("\"Hello\""))
    }

    @Test
    fun `renders full-width parentheses in gray and hides the brackets`() {
        val rendered = render("她低下头（心里有些紧张）没有说话。")
        assertEquals("她低下头心里有些紧张没有说话。", rendered.text)
        assertEquals(Color.Unspecified, rendered.colorOf("她低下头"))
        assertEquals(gray, rendered.colorOf("心里有些紧张"))
        assertEquals(Color.Unspecified, rendered.colorOf("没有说话。"))
    }

    @Test
    fun `keeps ASCII half-width parentheses unchanged`() {
        val rendered = render("时间是 (12:30) 左右（轻声叹气）")
        assertEquals("时间是 (12:30) 左右轻声叹气", rendered.text)
        assertEquals(Color.Unspecified, rendered.colorOf("(12:30)"))
        assertEquals(gray, rendered.colorOf("轻声叹气"))
    }

    @Test
    fun `handles full-width parentheses nested inside dialogue quotes`() {
        val rendered = render("“那个……（声音越来越小）我先走了。”")
        assertEquals("“那个……声音越来越小我先走了。”", rendered.text)
        assertEquals(orange, rendered.colorOf("“那个……"))
        assertEquals(gray, rendered.colorOf("声音越来越小"))
        assertEquals(orange, rendered.colorOf("我先走了。”"))
    }

    @Test
    fun `handles quotes inside full-width parentheses as gray without breaking outside quotes`() {
        val rendered = render("（她想起那句“别回头”）“走吧。”")
        assertEquals("她想起那句“别回头”“走吧。”", rendered.text)
        assertEquals(gray, rendered.colorOf("她想起那句“别回头”"))
        assertEquals(orange, rendered.colorOf("“走吧。”"))
    }

    @Test
    fun `handles nested full-width parentheses by hiding only the outermost pair`() {
        val rendered = render("（她摸了摸口袋（里面空空的），叹了口气）")
        assertEquals("她摸了摸口袋（里面空空的），叹了口气", rendered.text)
        assertEquals(gray, rendered.colorOf("她摸了摸口袋（里面空空的），叹了口气"))
    }

    @Test
    fun `preserves bold and italic styles inside quotes and parentheses`() {
        val rendered = render("“你**真的**要走吗？”（*轻轻*摇头）")
        assertEquals("“你真的要走吗？”轻轻摇头", rendered.text)
        assertEquals(orange, rendered.colorOf("真的"))
        assertEquals(gray, rendered.colorOf("轻轻"))
        assertTrue(rendered.spanStyles.any { it.item.fontWeight == FontWeight.Bold && rendered.text.substring(it.start, it.end) == "真的" })
        assertTrue(rendered.spanStyles.any { it.item.fontStyle == FontStyle.Italic && rendered.text.substring(it.start, it.end) == "轻轻" })
    }

    @Test
    fun `does not hide unclosed or empty full-width parentheses during streaming`() {
        val unclosed = render("她正想说（还没说完")
        assertEquals("她正想说（还没说完", unclosed.text)
        assertEquals(Color.Unspecified, unclosed.colorOf("（还没说完"))

        val empty = render("空白（）和（   ）保留")
        assertEquals("空白（）和（   ）保留", empty.text)
        assertEquals(Color.Unspecified, empty.colorOf("（）"))
    }

    @Test
    fun `does not override inline code or explicit html colors`() {
        val rendered = render("""`（代码）` 和 <span style="color: #ff0000">“红色警告”</span>""")
        assertEquals("（代码） 和 “红色警告”", rendered.text)
        assertEquals(Color.Green, rendered.colorOf("（代码）"))
        assertEquals(Color.Red, rendered.colorOf("“红色警告”"))
    }
}
