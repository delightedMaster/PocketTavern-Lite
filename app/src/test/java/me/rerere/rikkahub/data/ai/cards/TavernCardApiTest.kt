package me.rerere.rikkahub.data.ai.cards

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TavernCardApiTest {
    private val messages = listOf(
        CardMessage("a", "user", "first"),
        CardMessage("b", "assistant", "second"),
        CardMessage("c", "user", "third"),
    )

    private fun world(chat: Map<String, String> = mapOf("mood" to "calm")) = CardWorld(
        messages = messages,
        chatVariables = chat,
        globalVariables = mapOf("name" to "Ada"),
        messageVariables = mapOf("b" to mapOf("hp" to "3")),
        currentMessageId = "b",
    )

    @Test
    fun htmlDetectionIgnoresALoneBreak() {
        assertFalse(looksLikeCardHtml("hello<br>there"))
        assertTrue(looksLikeCardHtml("<div class='card'>hi</div>"))
        assertTrue(cardDocument("<div></div>").contains("getChatMessages"))
    }

    @Test
    fun networkAllowlistMatchesHostOrSubdomain() {
        assertTrue(cardRequestAllowed("data:text/plain,hi", emptyList()))
        assertFalse(cardRequestAllowed("https://cdn.example.com/a.js", emptyList()))
        assertFalse(cardRequestAllowed("file:///sdcard/secret", listOf("example.com")))
        assertFalse(cardRequestAllowed("javascript:alert(1)", listOf("example.com")))
        assertTrue(cardRequestAllowed("https://cdn.example.com/a.js", listOf("example.com")))
        assertTrue(cardRequestAllowed("https://example.com/a.js", listOf("example.com")))
        assertFalse(cardRequestAllowed("https://evil.com/a.js", listOf("example.com")))
    }

    @Test
    fun variableWritesMergeAndStayOnTheRequestedScope() {
        val effect = handleCardRequest(
            world(),
            """{"op":"setVariables","payload":{"variables":{"place":"inn"},"type":"chat"}}""",
        )
        assertEquals(mapOf("mood" to "calm", "place" to "inn"), effect.chatVariables)
        assertEquals(null, effect.globalVariables)
        val replaced = handleCardRequest(
            world(),
            """{"op":"replaceVariables","payload":{"variables":{"place":"road"},"type":"global"}}""",
        )
        assertEquals("Ada", replaced.globalVariables?.get("name"))
        assertEquals("road", replaced.globalVariables?.get("place"))
    }

    @Test
    fun mvuDataIsTheChatVariable() {
        val stored = handleCardRequest(
            world(),
            """{"op":"mvuReplace","payload":{"data":{"hp":9},"type":"chat"}}""",
        )
        assertTrue(stored.chatVariables?.get(MVU_VARIABLE)?.contains("hp") == true)
        val read = handleCardRequest(
            world(chat = mapOf(MVU_VARIABLE to """{"hp":9}""")),
            """{"op":"mvuGet","payload":{"type":"chat"}}""",
        )
        assertTrue(read.responseJson.contains(""""hp":9"""))
    }

    @Test
    fun messageRangeAndSlash() {
        assertEquals(listOf(messages[0]), selectMessages(messages, JsonPrimitive(0)))
        assertEquals(listOf(messages[2]), selectMessages(messages, JsonPrimitive(-1)))
        assertEquals(messages.take(2), selectMessages(messages, JsonPrimitive("0-1")))
        assertEquals(
            messages,
            selectMessages(messages, buildJsonObject {
                put("start", 0)
                put("end", 2)
            }),
        )
        val slash = handleCardRequest(world(), """{"op":"triggerSlash","payload":{"command":"/send hi"}}""")
        assertEquals("/send hi", slash.slash)
        val missing = handleCardRequest(world(), """{"op":"generateRaw","payload":{}}""")
        assertTrue(missing.responseJson.contains("unsupported"))
    }

    @Test
    fun getChatMessagesUsesStoredText() {
        val effect = handleCardRequest(world(), """{"op":"getChatMessages","payload":{"range":null}}""")
        assertTrue(effect.responseJson.contains("first"))
        assertTrue(effect.responseJson.contains("third"))
    }

    @Test
    fun composerOpsMapSendTextareaToTheAppInput() {
        val current = world().copy(input = "draft")
        val read = handleCardRequest(current, """{"op":"getInput","payload":{}}""")
        assertEquals("""{"result":"draft"}""", read.responseJson)

        val write = handleCardRequest(current, """{"op":"setInput","payload":{"text":"draft\n纯发癫"}}""")
        assertEquals("draft\n纯发癫", write.fillInput)
        assertFalse(write.sendInput)

        val send = handleCardRequest(current, """{"op":"sendInput","payload":{}}""")
        assertTrue(send.sendInput)
        assertEquals(null, send.fillInput)
    }

    @Test
    fun themeColorIsTheDefaultAndExplicitCardColorsStay() {
        val source = """
            <style>body{color:#ffffff}.name{color:#e53935}</style>
            <div class="name">甲</div><p>乙</p>
        """.trimIndent()
        val doc = cardDocument(source, textColor = "#112233", dark = true)
        assertTrue(doc.contains("color-scheme:dark"))
        assertTrue(doc.contains("color:#112233"))
        assertTrue(doc.contains("color:#ffffff"))
        assertTrue(doc.contains("color:#e53935"))
        assertTrue(doc.indexOf("pockettavern-theme") < doc.indexOf("color:#ffffff"))
        assertTrue(doc.indexOf("pockettavern-theme") < doc.indexOf("color:#e53935"))
    }

    @Test
    fun fullDocumentKeepsAuthorStylesAfterTheThemeDefault() {
        val source = """<html><head><style>p{color:#000000}</style></head><body><p>字</p></body></html>"""
        val doc = cardDocument(source, textColor = "#EEEEEE", dark = false)
        assertTrue(doc.contains("color-scheme:light"))
        assertTrue(doc.contains("color:#EEEEEE"))
        assertTrue(doc.contains("p{color:#000000}"))
        assertTrue(doc.indexOf("pockettavern-theme") < doc.indexOf("p{color:#000000}"))
    }

    @Test
    fun cardHeightFollowsContentUntilTheDevicePixelCeiling() {
        assertEquals(HtmlCardLayout(200, false), htmlCardLayoutHeight(200, density = 3f))
        assertEquals(HtmlCardLayout(32, false), htmlCardLayoutHeight(0, density = 2f))
        // 旧实现把高度钳在 720dp，2000 像素的正文后半段不在布局里
        assertEquals(HtmlCardLayout(2000, false), htmlCardLayoutHeight(2000, density = 3f))
        val capped = htmlCardLayoutHeight(20_000, density = 2.5f)
        assertTrue(capped.scrolls)
        assertEquals((HTML_CARD_MAX_DEVICE_PX / 2.5f).toInt(), capped.heightCssPx)
        assertTrue(capped.heightCssPx > 720)
    }

    @Test
    fun shimProvidesSendTextareaAndSendButton() {
        assertTrue(TAVERN_CARD_SHIM.contains("send_textarea"))
        assertTrue(TAVERN_CARD_SHIM.contains("send_but"))
        assertTrue(cardDocument("<div>x</div>").contains("installComposer"))
    }

    @Test
    fun `current message id is the floor index and round-trips through getChatMessages`() {
        // Izumi 1029 option panel: getChatMessages(getCurrentMessageId())[0].message
        val current = handleCardRequest(world(), """{"op":"getCurrentMessageId","payload":{}}""")
        assertEquals("""{"result":1}""", current.responseJson)
        val last = handleCardRequest(world(), """{"op":"getLastMessageId","payload":{}}""")
        assertEquals("""{"result":2}""", last.responseJson)
        val read = handleCardRequest(world(), """{"op":"getChatMessages","payload":{"range":1}}""")
        assertTrue(read.responseJson.contains(""""message_id":1"""))
        assertTrue(read.responseJson.contains(""""message":"second""""))
        assertTrue(TAVERN_CARD_SHIM.contains("getCurrentMessageId"))
    }
}
