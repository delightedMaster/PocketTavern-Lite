package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.*
import org.junit.Test

class ContextTokenBudgetTest {
    private fun message(role: MessageRole, text: String, synthetic: Boolean = false) =
        UIMessage(role = role, parts = listOf(UIMessagePart.Text(text)), isSynthetic = synthetic)

    @Test
    fun `budget removes a whole old turn while keeping static prompts and latest turn tools`() {
        val static = message(MessageRole.SYSTEM, "角色", true)
        val oldUser = message(MessageRole.USER, "旧".repeat(40))
        val oldReply = message(MessageRole.ASSISTANT, "答".repeat(40))
        val user = message(MessageRole.USER, "新问题")
        val tool = message(MessageRole.ASSISTANT, "当前工具调用")
        val tail = message(MessageRole.USER, "世界书", true)
        val source = listOf(static, oldUser, oldReply, user, tool, tail)
        assertEquals(listOf(static, user, tool, tail), fitContextTokenBudget(source, 100, 20))
        assertEquals(6, source.size)
    }

    @Test
    fun `reply and tool schema budgets are reserved before history`() {
        val oldUser = message(MessageRole.USER, "旧".repeat(20))
        val oldReply = message(MessageRole.ASSISTANT, "答".repeat(20))
        val latest = message(MessageRole.USER, "新")
        val messages = listOf(oldUser, oldReply, latest)
        assertEquals(messages, fitContextTokenBudget(messages, 90, null))
        assertEquals(listOf(latest), fitContextTokenBudget(messages, 90, 20, 20))
    }

    @Test
    fun `too much mandatory content raises an error instead of dropping the current prompt`() {
        val messages = listOf(message(MessageRole.SYSTEM, "设定".repeat(50), true), message(MessageRole.USER, "问题"))
        assertThrows(IllegalArgumentException::class.java) { fitContextTokenBudget(messages, 50, 10) }
        assertThrows(IllegalArgumentException::class.java) { fitContextTokenBudget(messages, 50, 50) }
    }

    @Test
    fun `unrestricted and large custom limits do not apply a hidden UI cap`() {
        val messages = listOf(message(MessageRole.USER, "中".repeat(9000)))
        assertSame(messages, fitContextTokenBudget(messages, null, null))
        assertEquals(messages, fitContextTokenBudget(messages, 2_000_000, 1_000_000))
    }
    @Test
    fun `summarization refuses to silently omit the messages it was asked to summarize`() {
        val messages = listOf(message(MessageRole.USER, "旧".repeat(100)), message(MessageRole.USER, "新"))
        assertThrows(IllegalArgumentException::class.java) {
            fitContextTokenBudget(messages, 50, null, allowHistoryTrim = false)
        }
    }

}
