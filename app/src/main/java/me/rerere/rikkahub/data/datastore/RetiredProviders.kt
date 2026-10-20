package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.ProviderSetting
import kotlin.uuid.Uuid

private data class RetiredProvider(val name: String, val baseUrl: String)

// IDs from older default lists. Retire empty presets without deleting user credentials or model references.
private val retiredProviders = mapOf(
    Uuid.parse("1b1395ed-b702-4aeb-8bc1-b681c4456953") to RetiredProvider("AiHubMix", "https://aihubmix.com/v1"),
    Uuid.parse("2a05506f-3a59-450a-a493-33a82bc85a81") to RetiredProvider("APIMart", "https://api.apimart.ai/v1"),
    Uuid.parse("da93779f-3956-48cc-82ef-67bb482eaaf7") to RetiredProvider("302.AI", "https://api.302.ai/v1"),
    Uuid.parse("aecf04fd-cb5c-4582-aed2-e8bf393923fd") to RetiredProvider("随想AI网关", "https://sui-xiang.com/v1"),
    Uuid.parse("afbc54ad-807e-4455-9594-7d7a546356ad") to RetiredProvider("MaruCode", "https://api.muteki.site/v1"),
)

internal fun List<ProviderSetting>.withoutRetiredPresets(): List<ProviderSetting> = mapNotNull { provider ->
    val retired = retiredProviders[provider.id] ?: return@mapNotNull provider
    val (key, url) = when (provider) {
        is ProviderSetting.OpenAI -> provider.apiKey to provider.baseUrl
        is ProviderSetting.Google -> provider.apiKey to provider.baseUrl
        is ProviderSetting.Claude -> provider.apiKey to provider.baseUrl
    }
    if (key.isBlank() && provider.models.isEmpty() && provider.customHeaders.isEmpty() &&
        provider.name == retired.name && url == retired.baseUrl
    ) null else provider.copyProvider(builtIn = false, description = {}, shortDescription = {})
}
