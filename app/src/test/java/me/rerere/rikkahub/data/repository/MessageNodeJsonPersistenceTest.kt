package me.rerere.rikkahub.data.repository

import kotlinx.serialization.builtins.ListSerializer
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `message_node.messages` 列存的就是这段 JSON。这里覆盖的是写入该列之前的编码和读出之后的解码，
 * 不是 SQLite 游标，也不是流式拼接本身。
 */
class MessageNodeJsonPersistenceTest {
    @Test
    fun `a long reply round trips through the message node json column`() {
        val text = "甲".repeat(20_000) + "\n" + "乙".repeat(100) + "\nhttps://example.com/tail"
        val stored = listOf(UIMessage.user("问"), UIMessage.assistant(text))
        val column = JsonInstant.encodeToString(ListSerializer(UIMessage.serializer()), stored)
        val loaded = JsonInstant.decodeFromString(ListSerializer(UIMessage.serializer()), column)
        assertEquals(stored.map { it.role to it.toText() }, loaded.map { it.role to it.toText() })
        assertEquals(text, loaded.last().toText())
    }
}