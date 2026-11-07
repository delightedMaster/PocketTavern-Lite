package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.assembly.PromptCacheState
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.export.LorebookSerializer
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.LorebookKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * SillyTavern prompt_order assembly, checked against what openai.js would send.
 * The preset mirrors the shape of Izumi 0819: setvar first, relative entries around
 * markers, an entry whose prompts[].enabled is false but which prompt_order enables,
 * an absolute entry, and an assistant prefill after chat history.
 */
class TavernPresetPromptTest {

    private fun presetJson(squash: Boolean) = """
    {
      "squash_system_messages": $squash,
      "scenario_format": "[Scenario: {{scenario}}]",
      "prompts": [
        { "identifier": "init", "name": "init", "content": "{{setvar::mood::calm}}INIT", "role": "system" },
        { "identifier": "main", "name": "main", "content": "MAIN", "role": "system" },
        { "identifier": "fmt", "name": "fmt", "content": "FORMAT <options>", "role": "system", "enabled": false },
        { "identifier": "off", "name": "off", "content": "OFF", "role": "system" },
        { "identifier": "guide", "name": "guide", "content": "GUIDE", "role": "user" },
        { "identifier": "charDescription", "name": "Char", "marker": true },
        { "identifier": "scenario", "name": "Scenario", "marker": true },
        { "identifier": "chatHistory", "name": "History", "marker": true },
        { "identifier": "deep", "name": "deep", "content": "ABS", "role": "system", "injection_position": 1, "injection_depth": 1 },
        { "identifier": "tail", "name": "tail", "content": "TAIL", "role": "system" },
        { "identifier": "prefill", "name": "prefill", "content": "<thinking>", "role": "assistant" }
      ],
      "prompt_order": [ { "character_id": 100001, "order": [
        { "identifier": "init", "enabled": true },
        { "identifier": "main", "enabled": true },
        { "identifier": "fmt", "enabled": true },
        { "identifier": "off", "enabled": false },
        { "identifier": "guide", "enabled": true },
        { "identifier": "charDescription", "enabled": true },
        { "identifier": "scenario", "enabled": true },
        { "identifier": "chatHistory", "enabled": true },
        { "identifier": "deep", "enabled": true },
        { "identifier": "tail", "enabled": true },
        { "identifier": "prefill", "enabled": true }
      ] } ]
    }
    """.trimIndent()

    private fun preset(squash: Boolean): Lorebook {
        val json = presetJson(squash)
        val entries = LorebookSerializer.tryImportPresetOrdered(json)!!
        return Lorebook(
            name = "izumi-like",
            kind = LorebookKind.PRESET,
            entries = entries,
            stPreset = LorebookSerializer.tryImportPresetSettings(json),
        )
    }

    private fun context(book: Lorebook): ChatTurnContext {
        val assistant = Assistant(systemPrompt = "CHAR", lorebookIds = setOf(book.id))
        return ChatTurnContext(
            settings = Settings(lorebooks = listOf(book)),
            assistant = assistant,
            model = Model(modelId = "test"),
            providerSetting = ProviderSetting.OpenAI(),
            conversationId = Uuid.random(),
            conversationSystemPrompt = null,
            conversationModeInjectionIds = emptySet(),
            conversationLorebookIds = emptySet(),
            memories = emptyList(),
            tools = emptyList(),
            promptState = PromptCacheState(),
        )
    }

    private val chat = listOf(
        UIMessage.user("hi"),
        UIMessage.assistant("hello"),
        UIMessage.user("go"),
    )

    @Test
    fun `import keeps st semantics`() {
        val book = preset(squash = false)
        assertNotNull(book.stPreset)
        val byName = book.entries.associateBy { it.name }
        assertTrue("prompt_order enables it; prompts[].enabled is ignored", byName.getValue("fmt").enabled)
        assertTrue(!byName.getValue("off").enabled)
        assertTrue(byName.getValue("History").stPrompt!!.marker)
        assertTrue(byName.getValue("deep").stPrompt!!.absolute)
        assertTrue(!byName.getValue("tail").stPrompt!!.absolute)
    }

    @Test
    fun `entries follow prompt_order around markers and history`() {
        val out = context(preset(squash = false)).layeredPrompt(chat).messages
        val shape = out.map { it.role to it.toText() }
        assertEquals(
            listOf(
                MessageRole.SYSTEM to "{{setvar::mood::calm}}INIT",
                MessageRole.SYSTEM to "MAIN",
                MessageRole.SYSTEM to "FORMAT <options>",
                MessageRole.USER to "GUIDE",
                MessageRole.SYSTEM to "CHAR",
                MessageRole.USER to "hi",
                MessageRole.ASSISTANT to "hello",
                // absolute depth 1: before the newest message
                MessageRole.SYSTEM to "ABS",
                MessageRole.USER to "go",
                // relative entries after chatHistory follow the whole history
                MessageRole.SYSTEM to "TAIL",
                MessageRole.ASSISTANT to "<thinking>",
            ),
            shape,
        )
        // History keeps its ids so frozen macro text and regex depth still line up
        assertEquals(chat[2].id, out[8].id)
        assertTrue(out.filter { it.toText() in setOf("MAIN", "TAIL", "<thinking>") }.all { it.isSynthetic })
    }

    @Test
    fun `squash merges runs of synthetic system messages only`() {
        val out = context(preset(squash = true)).layeredPrompt(chat).messages
        assertEquals(
            listOf(
                MessageRole.SYSTEM to "{{setvar::mood::calm}}INIT\nMAIN\nFORMAT <options>",
                MessageRole.USER to "GUIDE",
                MessageRole.SYSTEM to "CHAR",
                MessageRole.USER to "hi",
                MessageRole.ASSISTANT to "hello",
                MessageRole.SYSTEM to "ABS",
                MessageRole.USER to "go",
                MessageRole.SYSTEM to "TAIL",
                MessageRole.ASSISTANT to "<thinking>",
            ),
            out.map { it.role to it.toText() },
        )
    }

    @Test
    fun `in-chat injections group by depth, order and role like populationInjectionPrompts`() {
        val history = listOf(UIMessage.user("a"), UIMessage.assistant("b"), UIMessage.user("c"))
        val out = insertInChat(
            history,
            listOf(
                InChatInjection(depth = 0, order = 100, role = MessageRole.SYSTEM, text = "after-all"),
                InChatInjection(depth = 1, order = 100, role = MessageRole.SYSTEM, text = "sys1"),
                InChatInjection(depth = 1, order = 100, role = MessageRole.SYSTEM, text = "sys2"),
                InChatInjection(depth = 1, order = 100, role = MessageRole.USER, text = "usr"),
                InChatInjection(depth = 1, order = 50, role = MessageRole.SYSTEM, text = "early"),
                InChatInjection(depth = 99, order = 100, role = MessageRole.SYSTEM, text = "top"),
            ),
        )
        assertEquals(
            listOf("top", "a", "b", "early", "usr", "sys1\nsys2", "c", "after-all"),
            out.map { it.toText() },
        )
    }

    @Test
    fun `presets without prompt_order keep the layered path`() {
        val book = preset(squash = false).copy(stPreset = null)
        val ctx = context(book)
        assertEquals(null, ctx.activeTavernPreset())
    }
}
