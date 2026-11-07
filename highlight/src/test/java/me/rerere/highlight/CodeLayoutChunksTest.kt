package me.rerere.highlight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeLayoutChunksTest {
    @Test
    fun `long code keeps every line and each chunk stays highlightable`() {
        val lines = List(200) { index -> "val line$index = \"${"中".repeat(30)}\"" }
        val chunks = chunkCodeForLayout(lines)
        assertTrue(chunks.size > 1)
        assertEquals(lines, chunks.flatten())
        chunks.forEach { group ->
            assertTrue(group.size <= CODE_LAYOUT_MAX_LINES)
            assertTrue(group.joinToString("\n").length <= HIGHLIGHT_CHAR_LIMIT)
        }
    }

    @Test
    fun `a single very long line is not merged into the next lines`() {
        val lines = listOf("x".repeat(CODE_LAYOUT_MAX_CHARS + 10), "next")
        val chunks = chunkCodeForLayout(lines)
        assertEquals(listOf(listOf(lines[0]), listOf("next")), chunks)
    }

    @Test
    fun `markdown fences and blank lines survive the split`() {
        val lines = listOf("```kotlin", "fun main() {}", "```", "", "after")
        assertEquals(lines, chunkCodeForLayout(lines, maxChars = 12, maxLines = 2).flatten())
    }
}
