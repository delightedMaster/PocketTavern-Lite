package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Test

class RemovedCapabilitiesTest {
    @Test
    fun `old backups cannot enable image generation but retain vision and search`() {
        val visionModel = Model(
            inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
            outputModalities = listOf(Modality.TEXT, Modality.IMAGE),
            tools = setOf(BuiltInTools.Search, BuiltInTools.ImageGeneration),
        )
        val override = ProviderSetting.OpenAI(models = listOf(visionModel))
        val provider = ProviderSetting.OpenAI(models = listOf(visionModel.copy(providerOverwrite = override)))
        val settings = Settings(providers = listOf(provider)).withoutRemovedCapabilities()
        val model = settings.providers.single().models.single()

        assertEquals(visionModel.id, model.id)
        assertEquals(visionModel.inputModalities, model.inputModalities)
        assertEquals(listOf(Modality.TEXT), model.outputModalities)
        assertEquals(setOf(BuiltInTools.Search), model.tools)
        assertEquals(setOf(BuiltInTools.Search), model.providerOverwrite!!.models.single().tools)
        assertEquals(listOf(Modality.TEXT), model.providerOverwrite!!.models.single().outputModalities)
    }

    @Test
    fun `old assistant speech tool is removed while other tools remain enabled`() {
        val assistant = Assistant(localTools = listOf(LocalToolOption.TimeInfo, LocalToolOption.Tts))
        val settings = Settings(assistants = listOf(assistant)).withoutRemovedCapabilities()

        assertEquals(assistant.id, settings.assistants.single().id)
        assertEquals(listOf(LocalToolOption.TimeInfo), settings.assistants.single().localTools)
    }
}
