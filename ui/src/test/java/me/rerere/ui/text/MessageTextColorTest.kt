package me.rerere.ui.text

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MessageTextColorTest {
    private val theme = Color(0xFFE8E8E8)
    private val bubble = Color(0xFF1A237E)
    private val explicit = Color(0xFFE53935)

    @Test
    fun `streaming completion and history use the same default color`() {
        val streaming = resolveMessageTextColor(Color.Unspecified, theme, theme)
        val justFinished = resolveMessageTextColor(Color.Unspecified, theme, theme)
        val history = resolveMessageTextColor(Color.Unspecified, theme, theme)
        assertEquals(streaming, justFinished)
        assertEquals(justFinished, history)
        assertEquals(theme, justFinished)
        assertNotEquals(Color.Black, justFinished)
        assertNotEquals(Color.White, justFinished)
    }

    @Test
    fun `missing content color falls back to the theme instead of black`() {
        assertEquals(theme, resolveMessageTextColor(Color.Unspecified, Color.Unspecified, theme))
        assertNotEquals(Color.Black, resolveMessageTextColor(Color.Unspecified, Color.Unspecified, theme))
    }

    @Test
    fun `explicit paragraph and bubble colors are kept`() {
        assertEquals(explicit, resolveMessageTextColor(explicit, theme, theme))
        assertEquals(bubble, resolveMessageTextColor(Color.Unspecified, bubble, theme))
    }
}