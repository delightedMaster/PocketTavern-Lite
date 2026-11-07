package me.rerere.ui.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextLayoutChunksTest {
    @Test
    fun `short text stays one layout`() {
        val text = buildAnnotatedString { append("短回复") }
        assertEquals(listOf(text), text.splitForTextLayout())
    }

    @Test
    fun `long chinese paragraph keeps every character and explicit color`() {
        val body = "甲".repeat(2_000)
        val text = buildAnnotatedString {
            append(body)
            withStyle(SpanStyle(color = Color(0xFFE53935))) { append("红字") }
            append("乙".repeat(800))
        }
        val chunks = text.splitForTextLayout(maxChars = 1_200)
        assertTrue(chunks.size > 1)
        assertEquals(text.text, chunks.joinToString("") { it.text })
        chunks.forEach { assertTrue(it.length <= 1_200) }
        val red = chunks.flatMap { chunk -> chunk.spanStyles.map { chunk to it } }
            .single { it.second.item.color == Color(0xFFE53935) }
        val (chunk, span) = red
        assertEquals("红字", chunk.text.substring(span.start, span.end))
    }

    @Test
    fun `multi paragraph text splits on a newline inside the window`() {
        val paragraph = "段".repeat(400)
        val text = List(6) { paragraph }.joinToString("\n")
        val ranges = layoutChunkRanges(text, maxChars = 1_200)
        assertTrue(ranges.size > 1)
        assertEquals(text, ranges.joinToString("") { text.substring(it.first, it.second) })
        ranges.dropLast(1).forEach { (start, end) ->
            assertEquals('\n', text[end - 1])
            assertTrue(end - start <= 1_200)
        }
    }

    @Test
    fun `a chunk boundary does not split a surrogate pair`() {
        val emoji = "😀"
        assertEquals(2, emoji.length)
        val text = "a".repeat(1_199) + emoji + "b".repeat(50)
        val ranges = layoutChunkRanges(text, maxChars = 1_200)
        val joined = ranges.joinToString("") { text.substring(it.first, it.second) }
        assertEquals(text, joined)
        ranges.forEach { (start, end) ->
            if (start in 1 until text.length) assertTrue(!text[start].isLowSurrogate())
            if (end in 1 until text.length) assertTrue(!text[end].isLowSurrogate())
        }
    }

    @Test
    fun `chunking keeps newlines and a link that sits on the boundary`() {
        val text = buildAnnotatedString {
            append("甲".repeat(40))
            withLink(LinkAnnotation.Url("https://example.com/a")) { append("链接文字") }
            append("\n")
            withStyle(SpanStyle(color = Color(0xFF1565C0))) { append("乙".repeat(80)) }
        }
        val chunks = text.splitForTextLayout(maxChars = 50)
        assertTrue(chunks.size > 1)
        assertEquals(text.text, chunks.joinToString("") { it.text })
        val urls = chunks.flatMap { chunk ->
            chunk.getLinkAnnotations(0, chunk.length).map { it.item }
        }
        assertTrue(urls.filterIsInstance<LinkAnnotation.Url>().any { it.url == "https://example.com/a" })
        val blue = chunks.flatMap { chunk -> chunk.spanStyles.map { it to chunk } }
            .filter { (span, _) -> span.item.color == Color(0xFF1565C0) }
        assertEquals("乙".repeat(80), blue.joinToString("") { (span, chunk) ->
            chunk.text.substring(span.start, span.end)
        })
    }
}
