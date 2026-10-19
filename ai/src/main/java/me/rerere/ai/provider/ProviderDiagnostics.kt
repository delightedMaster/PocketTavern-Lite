package me.rerere.ai.provider

import kotlinx.coroutines.withTimeout
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

/** Runs actual provider requests without creating a conversation or executing returned tools. */
class ProviderDiagnostics<T : ProviderSetting>(
    private val provider: Provider<T>,
    private val setting: T,
    private val timeoutMillis: Long = 90_000,
) {
    suspend fun listModels(): List<Model> = withTimeout(timeoutMillis) {
        provider.listModels(setting)
    }

    suspend fun send(
        model: Model,
        message: String,
        streaming: Boolean,
        onText: (String) -> Unit = {},
    ): String = withTimeout(timeoutMillis) {
        require(model.modelId.isNotBlank()) { "请先填写或选择模型 ID" }
        require(message.isNotBlank()) { "请填写测试消息" }
        val messages = listOf(UIMessage.user(message))
        val params = model.testParams()
        val text = if (streaming) {
            buildString {
                provider.streamText(setting, messages, params).collect { chunk ->
                    if (chunk is StreamChunk.TextDelta) {
                        append(chunk.text)
                        onText(toString())
                    }
                }
            }
        } else {
            provider.generateText(setting, messages, params).message.text()
        }
        check(text.isNotBlank()) { "接口已返回，但没有收到文本回复；请检查模型、接口格式或内容限制" }
        text
    }

    suspend fun testTools(model: Model): String = withTimeout(timeoutMillis) {
        require(model.modelId.isNotBlank()) { "请先填写或选择模型 ID" }
        val tool = Tool(
            name = "connection_test",
            description = "A harmless diagnostic tool. Call this tool to verify tool calling.",
            execute = { emptyList() },
        )
        val result = provider.generateText(
            setting,
            listOf(UIMessage.user("Call the connection_test tool once.")),
            model.testParams().copy(tools = listOf(tool)),
        )
        val call = result.message.parts.filterIsInstance<UIMessagePart.Tool>()
            .firstOrNull { it.toolName == tool.name }
        check(call != null) {
            "接口已返回，但模型没有调用测试工具；这不影响普通聊天。回复：${result.message.text()}"
        }
        "${call.toolName}(${call.input})"
    }

    private fun Model.testParams() = TextGenerationParams(
        model = this,
        customHeaders = customHeaders,
        customBody = customBodies,
        reasoningLevel = ReasoningLevel.AUTO,
        sessionId = null,
    )

    private fun UIMessage.text() = parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }
}

val ProviderSetting.textApiLabel: String
    get() = when (this) {
        is ProviderSetting.OpenAI -> if (useResponseApi) "OpenAI Responses" else "OpenAI Chat Completions"
        is ProviderSetting.Claude -> "Anthropic Messages"
        is ProviderSetting.Google -> when {
            vertexAI -> "Vertex AI generateContent"
            useInteractionsApi -> "Gemini Interactions (Beta)"
            else -> "Gemini generateContent"
        }
    }
