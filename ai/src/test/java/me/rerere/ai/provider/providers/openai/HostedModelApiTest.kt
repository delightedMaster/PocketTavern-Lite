package me.rerere.ai.provider.providers.openai

import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.*
import org.junit.Test

class HostedModelApiTest {
    private val go = ProviderSetting.OpenAI(baseUrl = "https://opencode.ai/zen/go/v1")
    private val command = ProviderSetting.OpenAI(baseUrl = "https://api.commandcode.ai/provider/v1")

    @Test
    fun `OpenCode Go uses the documented protocol for each model family`() {
        for (model in listOf("glm-5.3", "kimi-k3", "deepseek-v4-flash", "mimo-v2.5")) {
            assertEquals(HostedModelApi.CHAT_COMPLETIONS, go.hostedModelApi(model))
        }
        for (model in listOf("gpt-5.6-luna", "grok-4.6", "muse-spark-1.3-contributor")) {
            assertEquals(HostedModelApi.RESPONSES, go.hostedModelApi(model))
        }
        for (model in listOf("minimax-m2.7", "qwen3.8-flash", "claude-haiku-5-5")) {
            assertEquals(HostedModelApi.MESSAGES, go.hostedModelApi(model))
        }
    }

    @Test
    fun `Command Code Claude models use Messages while other models respect Responses switch`() {
        assertEquals(HostedModelApi.MESSAGES, command.hostedModelApi("claude-sonnet-4-6"))
        assertEquals(HostedModelApi.MESSAGES, command.copy(useResponseApi = true).hostedModelApi("claude-sonnet-4-6"))
        assertEquals(HostedModelApi.CHAT_COMPLETIONS, command.hostedModelApi("deepseek/deepseek-v4-flash"))
        assertEquals(HostedModelApi.RESPONSES, command.copy(useResponseApi = true).hostedModelApi("gpt-5.6-sol"))
    }

    @Test
    fun `routing is restricted to exact provider hosts and endpoint paths`() {
        assertEquals(HostedModelApi.CHAT_COMPLETIONS, go.copy(baseUrl = "https://opencode.ai.example/zen/go/v1").hostedModelApi("claude-haiku-5-5"))
        assertEquals(HostedModelApi.CHAT_COMPLETIONS, go.copy(baseUrl = "https://opencode.ai/zen/v1").hostedModelApi("claude-haiku-5-5"))
        assertEquals(HostedModelApi.MESSAGES, go.copy(baseUrl = "https://opencode.ai/inference/go/openai/v1").hostedModelApi("minimax-m3"))
    }

    @Test
    fun `Messages delegation reads current credentials without storing model overrides`() {
        val updated = command.copy(apiKey = "new-test-key")
        val derived = updated.asMessagesProvider()
        assertEquals(updated.id, derived.id)
        assertEquals("new-test-key", derived.apiKey)
        assertEquals(updated.baseUrl, derived.baseUrl)
        assertTrue(derived.models.isEmpty())
    }
    @Test
    fun `new Go endpoint keeps its model catalog and Messages base on the documented routes`() {
        val newer = go.copy(baseUrl = "https://opencode.ai/inference/go/openai/v1")
        assertEquals("https://opencode.ai/inference/go/v1/models", newer.hostedModelsUrl())
        assertEquals("https://opencode.ai/inference/go/anthropic/v1", newer.asMessagesProvider().baseUrl)
        assertEquals("https://opencode.ai/zen/go/v1/models", go.hostedModelsUrl())
    }

}
