package me.rerere.ai.util

import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.ProviderSetting
import okhttp3.Request
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestHeadersTest {
    @Test
    fun `mergeCustomHeaders should use provider headers when no request headers`() {
        val provider = ProviderSetting.OpenAI(
            customHeaders = listOf(
                CustomHeader("User-Agent", "provider-agent"),
                CustomHeader("X-Title", "provider-title"),
            )
        )

        val headers = provider.mergeCustomHeaders()

        assertEquals(listOf("provider-agent"), headers.values("User-Agent"))
        assertEquals(listOf("provider-title"), headers.values("X-Title"))
    }

    @Test
    fun `mergeCustomHeaders should let request headers override provider headers`() {
        val provider = ProviderSetting.OpenAI(
            customHeaders = listOf(
                CustomHeader("User-Agent", "provider-agent"),
                CustomHeader("X-Title", "provider-title"),
            )
        )

        val headers = provider.mergeCustomHeaders(
            listOf(CustomHeader("user-agent", "model-agent"))
        )

        assertEquals(listOf("model-agent"), headers.values("User-Agent"))
        assertEquals(listOf("provider-title"), headers.values("X-Title"))
    }

    @Test
    fun `mergeCustomHeaders should ignore blank header names`() {
        val provider = ProviderSetting.OpenAI(
            customHeaders = listOf(CustomHeader("", "value"))
        )

        assertEquals(0, provider.mergeCustomHeaders().size)
    }
    @Test
    fun `OpenCode uses honest client identity and the stable conversation session header`() {
        fun request() = Request.Builder().url("https://opencode.ai/zen/go/v1/responses")
            .configureReferHeaders("https://opencode.ai/zen/go/v1")
            .configureSessionHeaders("https://opencode.ai/zen/go/v1", "conversation-id")
            .build()
        assertEquals("PocketTavern-Lite", request().header("User-Agent"))
        assertEquals("conversation-id", request().header("x-opencode-session"))
        val custom = Request.Builder().url("https://opencode.ai/zen/go/v1")
            .header("User-Agent", "custom-client/1.0")
            .configureReferHeaders("https://opencode.ai/zen/go/v1").build()
        assertEquals("custom-client/1.0", custom.header("User-Agent"))
    }

    @Test
    fun `Messages authentication adds Bearer only to the documented hosted APIs`() {
        for (host in listOf("opencode.ai", "api.commandcode.ai")) {
            val request = Request.Builder().url("https://$host/v1/messages")
                .configureClaudeApiKey("https://$host/v1", "test-key").build()
            assertEquals("Bearer test-key", request.header("Authorization"))
            assertEquals("test-key", request.header("x-api-key"))
        }
        val official = Request.Builder().url("https://api.anthropic.com/v1/messages")
            .configureClaudeApiKey("https://api.anthropic.com/v1", "test-key").build()
        assertNull(official.header("Authorization"))
    }

    @Test
    fun `removed sponsor no longer receives a hardcoded referral header`() {
        val request = Request.Builder().url("https://aihubmix.com/v1/models")
            .configureReferHeaders("https://aihubmix.com/v1").build()
        assertNull(request.header("APP-Code"))
    }

    @Test
    fun `official Anthropic retains its existing custom header composition`() {
        val request = Request.Builder().url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", "custom-header-key")
            .configureClaudeApiKey("https://api.anthropic.com/v1", "field-key").build()
        assertEquals(listOf("custom-header-key", "field-key"), request.headers.values("x-api-key"))
        assertNull(request.header("Authorization"))
    }

}
