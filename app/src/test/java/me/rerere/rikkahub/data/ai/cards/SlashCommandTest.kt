package me.rerere.rikkahub.data.ai.cards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlashCommandTest {
    private val replies = listOf(
        "wave" to "/send hello",
        "again" to "/trigger wave",
        "loop" to "/trigger loop",
    )

    @Test
    fun pipesAndPlainText() {
        val actions = compileSlashScript(
            "/setvar mood calm | /setvar place=inn\nhello",
            SlashContext(variables = mapOf("mood" to "old")),
        )
        assertEquals(
            listOf(
                SlashAction.SetVar("mood", "calm"),
                SlashAction.SetVar("place", "inn"),
                SlashAction.Send("hello"),
            ),
            actions,
        )
    }

    @Test
    fun supportedCommands() {
        val context = SlashContext(replies = replies, variables = mapOf("mood" to "calm"))
        assertEquals(listOf(SlashAction.Generate(null)), compileSlashScript("/gen", context))
        assertEquals(listOf(SlashAction.Generate("go")), compileSlashScript("/gen go", context))
        assertEquals(listOf(SlashAction.FillInput("calm")), compileSlashScript("/getvar mood", context))
        assertEquals(listOf(SlashAction.AskInput("name?")), compileSlashScript("/input name?", context))
        assertEquals(listOf(SlashAction.Send("hello")), compileSlashScript("/trigger wave", context))
        assertEquals(listOf(SlashAction.Send("hello")), compileSlashScript("/trigger again", context))
        assertTrue(compileSlashScript("/if true", context).single() is SlashAction.Unknown)
    }

    @Test
    fun triggerStopsInsteadOfLooping() {
        val actions = compileSlashScript("/trigger loop", SlashContext(replies = replies))
        assertTrue(actions.isEmpty())
    }
}
