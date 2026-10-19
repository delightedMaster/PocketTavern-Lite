package me.rerere.ai.provider

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.*
import org.junit.Test

class ProviderDiagnosticsTest {
    private val setting = ProviderSetting.OpenAI()
    private val model = Model(
        modelId = "test-model",
        customHeaders = listOf(CustomHeader("X-Test", "configured")),
        customBodies = listOf(CustomBody("custom", JsonPrimitive(true))),
    )

    @Test
    fun `failed model listing does not prevent sending with a manual model id`() = runTest {
        val provider = FakeProvider().apply { listFailure = IllegalStateException("HTTP 403") }
        val diagnostics = ProviderDiagnostics(provider, setting)
        assertEquals("HTTP 403", failure { diagnostics.listModels() }.message)
        assertEquals("reply", diagnostics.send(model, "my test message", false))
        assertEquals("my test message", (provider.messages.single().parts.single() as UIMessagePart.Text).text)
        assertEquals(model, provider.params!!.model)
        assertEquals(model.customHeaders, provider.params!!.customHeaders)
        assertEquals(model.customBodies, provider.params!!.customBody)
        assertTrue(provider.params!!.tools.isEmpty())
        assertEquals(1, provider.generations)
    }

    @Test
    fun `stream is collected once with incremental text and no extra generation request`() = runTest {
        val provider = FakeProvider().apply {
            stream = flowOf(StreamChunk.TextDelta("text", "hello"), StreamChunk.TextDelta("text", " world"))
        }
        val updates = mutableListOf<String>()
        assertEquals("hello world", ProviderDiagnostics(provider, setting).send(model, "test", true, updates::add))
        assertEquals(listOf("hello", "hello world"), updates)
        assertEquals(0, provider.generations)
        assertEquals(1, provider.streams)
    }

    @Test
    fun `empty regular and streaming responses fail instead of claiming success`() = runTest {
        val provider = FakeProvider().apply { reply = UIMessage.assistant(" ") }
        val diagnostics = ProviderDiagnostics(provider, setting)
        assertTrue(failure { diagnostics.send(model, "test", false) } is IllegalStateException)
        assertTrue(failure { diagnostics.send(model, "test", true) } is IllegalStateException)
    }

    @Test
    fun `invalid input does not make a request`() = runTest {
        val provider = FakeProvider()
        val diagnostics = ProviderDiagnostics(provider, setting)
        assertTrue(failure { diagnostics.send(Model(), "test", false) } is IllegalArgumentException)
        assertTrue(failure { diagnostics.send(model, " ", true) } is IllegalArgumentException)
        assertEquals(0, provider.generations + provider.streams)
    }

    @Test
    fun `tool test fails when expected tool was not returned`() = runTest {
        val provider = FakeProvider()
        assertTrue(failure { ProviderDiagnostics(provider, setting).testTools(model) } is IllegalStateException)
        assertEquals("connection_test", provider.params!!.tools.single().name)
    }

    @Test
    fun `tool test reports returned call without executing it or sending followup requests`() = runTest {
        val provider = FakeProvider().apply {
            reply = UIMessage.assistant("").copy(parts = listOf(UIMessagePart.Tool("call", "connection_test", "{}")))
        }
        assertEquals("connection_test({})", ProviderDiagnostics(provider, setting).testTools(model))
        assertEquals(1, provider.generations)
    }

    @Test
    fun `diagnostics time out and preserve caller cancellation`() = runTest {
        val provider = FakeProvider().apply { hang = true }
        assertTrue(failure { ProviderDiagnostics(provider, setting, 50).send(model, "test", false) } is TimeoutCancellationException)
        assertTrue(failure { ProviderDiagnostics(provider, setting, 50).listModels() } is TimeoutCancellationException)
        val cancellation = CancellationException("dialog closed")
        provider.hang = false
        provider.generationFailure = cancellation
        val error = failure { ProviderDiagnostics(provider, setting).send(model, "test", false) }
        assertTrue(error is CancellationException)
        assertEquals("dialog closed", error.message)
    }

    @Test
    fun `protocol labels match active API including vertex override`() {
        assertEquals("OpenAI Chat Completions", setting.textApiLabel)
        assertEquals("OpenAI Responses", setting.copy(useResponseApi = true).textApiLabel)
        assertEquals("Anthropic Messages", ProviderSetting.Claude().textApiLabel)
        assertEquals("Gemini generateContent", ProviderSetting.Google().textApiLabel)
        assertEquals("Gemini Interactions (Beta)", ProviderSetting.Google(useInteractionsApi = true).textApiLabel)
        assertEquals("Vertex AI generateContent", ProviderSetting.Google(vertexAI = true, useInteractionsApi = true).textApiLabel)
    }

    private suspend fun failure(block: suspend () -> Any?): Throwable {
        try { block() } catch (error: Throwable) { return error }
        throw AssertionError("Expected failure")
    }

    private class FakeProvider : Provider<ProviderSetting.OpenAI> {
        var reply = UIMessage.assistant("reply")
        var stream: Flow<StreamChunk> = emptyFlow()
        var messages = emptyList<UIMessage>()
        var params: TextGenerationParams? = null
        var listFailure: Exception? = null
        var generationFailure: Exception? = null
        var hang = false
        var generations = 0
        var streams = 0

        override suspend fun listModels(providerSetting: ProviderSetting.OpenAI): List<Model> {
            if (hang) awaitCancellation()
            listFailure?.let { throw it }
            return listOf(Model(modelId = "listed-model"))
        }

        override suspend fun generateText(providerSetting: ProviderSetting.OpenAI, messages: List<UIMessage>, params: TextGenerationParams): TextGenerationResult {
            generations++
            this.messages = messages
            this.params = params
            if (hang) awaitCancellation()
            generationFailure?.let { throw it }
            return TextGenerationResult("result", params.model.modelId, reply)
        }

        override suspend fun streamText(providerSetting: ProviderSetting.OpenAI, messages: List<UIMessage>, params: TextGenerationParams): Flow<StreamChunk> {
            streams++
            this.messages = messages
            this.params = params
            return stream
        }
    }
}
