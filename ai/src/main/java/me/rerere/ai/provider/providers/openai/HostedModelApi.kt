package me.rerere.ai.provider.providers.openai

import me.rerere.ai.provider.ProviderSetting
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal enum class HostedModelApi { CHAT_COMPLETIONS, RESPONSES, MESSAGES }

internal val ProviderSetting.OpenAI.isOpenCodeGo: Boolean
    get() = baseUrl.toHttpUrlOrNull()?.let {
        it.host == "opencode.ai" && (it.encodedPath.trimEnd('/') == "/zen/go/v1" ||
            it.encodedPath.trimEnd('/') == "/inference/go/openai/v1")
    } == true

internal val ProviderSetting.OpenAI.isCommandCode: Boolean
    get() = baseUrl.toHttpUrlOrNull()?.let {
        it.host == "api.commandcode.ai" && it.encodedPath.trimEnd('/') == "/provider/v1"
    } == true

/** These hosted catalogs expose different wire protocols for different model families. */
internal fun ProviderSetting.OpenAI.hostedModelApi(modelId: String): HostedModelApi {
    val model = modelId.substringAfterLast('/').lowercase()
    if ((isCommandCode || isOpenCodeGo) && model.startsWith("claude-")) return HostedModelApi.MESSAGES
    if (isOpenCodeGo) {
        if (model.startsWith("minimax-") || model.startsWith("qwen")) return HostedModelApi.MESSAGES
        if (model.startsWith("gpt-") || model.startsWith("grok-") || model.startsWith("muse-spark-")) {
            return HostedModelApi.RESPONSES
        }
    }
    return if (useResponseApi) HostedModelApi.RESPONSES else HostedModelApi.CHAT_COMPLETIONS
}

internal fun ProviderSetting.OpenAI.asMessagesProvider() = ProviderSetting.Claude(
    id = id,
    name = name,
    apiKey = apiKey,
    baseUrl = if (isOpenCodeGo && baseUrl.trimEnd('/').endsWith("/inference/go/openai/v1")) {
        "https://opencode.ai/inference/go/anthropic/v1"
    } else baseUrl.trimEnd('/'),
    customHeaders = customHeaders,
    enabled = enabled,
)

internal fun ProviderSetting.OpenAI.hostedModelsUrl(): String =
    if (isOpenCodeGo && baseUrl.trimEnd('/').endsWith("/inference/go/openai/v1")) {
        "https://opencode.ai/inference/go/v1/models"
    } else "${baseUrl.trimEnd('/')}/models"
