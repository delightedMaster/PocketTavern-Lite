package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 前端卡的 HTML 挂在 markdownOnly 正则的 replaceString 上，只有显示层会用它。
 * 提示词层那条链的产物会被写回会话（ChatService 消费 GenerationChunk.Messages），
 * 带上它就会把整份卡片 HTML 灌进消息正文，所以两层必须是两个 transformer。
 */
class RegexDisplayLayerTest {

    @Test
    fun `a rule that is not idempotent runs once inside each layer`() {
        val regex = me.rerere.rikkahub.data.model.AssistantRegex(
            id = kotlin.uuid.Uuid.random(),
            name = "dup",
            findRegex = "x",
            replaceString = "xx",
            affectingScope = setOf(AssistantAffectScope.ASSISTANT),
        )
        val assistant = Assistant(name = "a", regexes = listOf(regex))
        val messages = listOf(UIMessage.assistant("x"))
        assertEquals("xx", messages.replaceRegexesForDisplay(assistant, null, visual = false)[0].toText())
        assertEquals("xx", messages.replaceRegexesForDisplay(assistant, null, visual = true)[0].toText())
        val stored = messages
        val shown = stored.replaceRegexesForDisplay(assistant, null, visual = true)[0].toText()
        val chained = stored
            .replaceRegexesForDisplay(assistant, null, visual = false)
            .replaceRegexesForDisplay(assistant, null, visual = true)[0]
            .toText()
        assertEquals("xx", shown)
        assertEquals("xxxx", chained)
    }

    @Test
    fun `display layer keeps user messages untouched`() {
        val regex = me.rerere.rikkahub.data.model.AssistantRegex(
            id = kotlin.uuid.Uuid.random(),
            name = "only-assistant",
            findRegex = "hi",
            replaceString = "bye",
            affectingScope = setOf(AssistantAffectScope.ASSISTANT),
        )
        val assistant = Assistant(name = "a", regexes = listOf(regex))
        val messages = listOf(UIMessage.user("hi"))
        assertEquals("hi", messages.replaceRegexesForDisplay(assistant, null, visual = true)[0].toText())
    }
}
