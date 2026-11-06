package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseTavernRegexTest {

    @Test
    fun `plain pattern compiles`() {
        val result = parseTavernRegex("abc\\d+")
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().containsMatchIn("abc123"))
    }

    @Test
    fun `slash form with i flag is case insensitive`() {
        val result = parseTavernRegex("/abc/i")
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().containsMatchIn("ABC"))
    }

    @Test
    fun `slash form with JS-only flags is accepted`() {
        val result = parseTavernRegex("/abc/gmu")
        assertTrue(result.isSuccess)
        assertEquals("abc", result.getOrThrow().pattern)
        assertFalse(result.getOrThrow().containsMatchIn("ABC"))
    }

    @Test
    fun `unknown flag fails and names the flag`() {
        val result = parseTavernRegex("/abc/z")
        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message, message.contains("z"))
    }
}
