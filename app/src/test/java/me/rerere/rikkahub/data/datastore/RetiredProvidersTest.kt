package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class RetiredProvidersTest {
    private val retired = ProviderSetting.OpenAI(
        id = Uuid.parse("2a05506f-3a59-450a-a493-33a82bc85a81"),
        name = "APIMart",
        baseUrl = "https://api.apimart.ai/v1",
        builtIn = true,
    )

    @Test
    fun `upgrade removes unused retired presets and does not reinsert them`() {
        val upgraded = Settings(providers = listOf(retired)).withBuiltInDefaults()
        assertFalse(upgraded.providers.any { it.id == retired.id })
        assertTrue(upgraded.providers.any { it.name == "OpenCode Go" })
    }

    @Test
    fun `configured retired connection becomes custom and retains credentials and model IDs`() {
        val model = Model(modelId = "configured-model")
        val configured = retired.copy(apiKey = "test-key", models = listOf(model))
        val preserved = Settings(providers = listOf(configured)).withBuiltInDefaults().providers.single { it.id == retired.id } as ProviderSetting.OpenAI
        assertFalse(preserved.builtIn)
        assertEquals("test-key", preserved.apiKey)
        assertEquals(model.id, preserved.models.single().id)
    }

    @Test
    fun `user-created connections and customized endpoints are retained`() {
        val custom = retired.copy(id = Uuid.random())
        val changed = retired.copy(baseUrl = "https://custom.example/v1")
        assertEquals(listOf(custom), listOf(custom).withoutRetiredPresets())
        assertEquals(changed.baseUrl, (listOf(changed).withoutRetiredPresets().single() as ProviderSetting.OpenAI).baseUrl)
    }
}
