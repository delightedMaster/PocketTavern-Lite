package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultProvidersTest {
    @Test
    fun `retired sponsors are absent and independent Vertex and coding plans are present`() {
        val names = DEFAULT_PROVIDERS.map { it.name }.toSet()
        assertTrue(names.intersect(setOf("APIMart", "302.AI", "随想AI网关", "MaruCode", "AiHubMix")).isEmpty())
        assertEquals(22, DEFAULT_PROVIDERS.size)
        val vertex = DEFAULT_PROVIDERS.single { it.name == "Google Vertex AI" } as ProviderSetting.Google
        assertTrue(vertex.vertexAI)
        assertFalse((DEFAULT_PROVIDERS.single { it.name == "Gemini" } as ProviderSetting.Google).vertexAI)
        val go = DEFAULT_PROVIDERS.single { it.name == "OpenCode Go" } as ProviderSetting.OpenAI
        assertEquals("https://opencode.ai/zen/go/v1", go.baseUrl)
        assertEquals("PocketTavern-Lite", go.customHeaders.single { it.name == "User-Agent" }.value)
        assertEquals("https://api.commandcode.ai/provider/v1", (DEFAULT_PROVIDERS.single { it.name == "Command Code GOAT" } as ProviderSetting.OpenAI).baseUrl)
    }

    @Test
    fun `additional official providers use OpenAI compatible endpoints`() {
        val expected = mapOf(
            "Mistral AI" to "https://api.mistral.ai/v1",
            "Groq" to "https://api.groq.com/openai/v1",
            "Cerebras" to "https://api.cerebras.ai/v1",
        )
        expected.forEach { (name, url) ->
            val provider = DEFAULT_PROVIDERS.single { it.name == name } as ProviderSetting.OpenAI
            assertEquals(url, provider.baseUrl)
            assertFalse(provider.enabled)
            assertFalse(provider.useResponseApi)
        }
    }

    @Test
    fun `official Claude preset uses the native Anthropic protocol`() {
        val claude = DEFAULT_PROVIDERS.filterIsInstance<ProviderSetting.Claude>()
            .single { it.name == "Anthropic (Claude)" }
        assertEquals("https://api.anthropic.com/v1", claude.baseUrl)
        assertTrue(claude.builtIn)
        assertFalse(claude.enabled)
        assertEquals(DEFAULT_PROVIDERS.size, DEFAULT_PROVIDERS.map { it.id }.distinct().size)
    }

    @Test
    fun `default providers should include vercel ai gateway with expected balance config`() {
        val vercelProviders = DEFAULT_PROVIDERS
            .filterIsInstance<ProviderSetting.OpenAI>()
            .filter { it.name == "Vercel AI Gateway" }

        assertEquals(1, vercelProviders.size)

        val provider = vercelProviders.single()
        assertEquals("https://ai-gateway.vercel.sh/v1", provider.baseUrl)
        assertFalse(provider.enabled)
        assertTrue(provider.builtIn)
        assertTrue(provider.balanceOption.enabled)
        assertEquals("/credits", provider.balanceOption.apiPath)
        assertEquals("balance", provider.balanceOption.resultPath)
    }
}
