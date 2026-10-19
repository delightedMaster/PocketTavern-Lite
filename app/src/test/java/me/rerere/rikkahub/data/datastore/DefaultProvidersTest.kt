package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultProvidersTest {
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
