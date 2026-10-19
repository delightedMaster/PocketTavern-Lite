package me.rerere.ai.provider.providers.openai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.util.KeyRoulette
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ChatCompletionsCacheKeyTest {
    private lateinit var api: ChatCompletionsAPI

    @Before
    fun setUp() {
        api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())
    }

    private fun buildRequest(baseUrl: String, sessionId: String?): JsonObject {
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "buildChatCompletionRequest",
            List::class.java,
            TextGenerationParams::class.java,
            ProviderSetting.OpenAI::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        return method.invoke(
            api,
            listOf(UIMessage.user("hi")),
            TextGenerationParams(model = Model(modelId = "test"), sessionId = sessionId),
            ProviderSetting.OpenAI(baseUrl = baseUrl),
            false,
        ) as JsonObject
    }

    @Test
    fun `openai requests carry a stable prompt cache key and sorted keys`() {
        val body = buildRequest("https://api.openai.com/v1", "chat-1")
        assertEquals("chat-1", body["prompt_cache_key"]!!.jsonPrimitive.content)
        assertEquals(body.keys.sorted(), body.keys.toList())
    }

    @Test
    fun `deepseek requests carry a stable user field`() {
        val body = buildRequest("https://api.deepseek.com/v1", "chat-1")
        assertEquals("chat-1", body["user"]!!.jsonPrimitive.content)
        assertNull(body["prompt_cache_key"])
    }
}
