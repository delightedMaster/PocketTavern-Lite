package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.*
import org.junit.Test

class AssistantDefaultsTest {
    @Test
    fun `both default templates can be removed after importing a role and remain removed after reload`() {
        val character = Assistant(name = "Imported role")
        val original = Settings(assistants = DEFAULT_ASSISTANTS + character)
        val deleted = DEFAULT_ASSISTANTS.fold(original) { settings, template -> settings.withoutAssistant(template.id) }
        val restored = JsonInstant.decodeFromString(Settings.serializer(), JsonInstant.encodeToString(Settings.serializer(), deleted)).withBuiltInDefaults()
        assertEquals(listOf(character.id), restored.assistants.map { it.id })
        assertEquals(character.id, restored.assistantId)
    }

    @Test
    fun `deleting the active role selects an existing role and never deletes the last one`() {
        val first = Assistant(name = "first")
        val second = Assistant(name = "second")
        val updated = Settings(assistants = listOf(first, second), assistantId = first.id).withoutAssistant(first.id)
        assertEquals(second.id, updated.assistantId)
        assertSame(updated, updated.withoutAssistant(second.id))
    }

    @Test
    fun `missing templates are not added to a customized library but empty backups remain safe`() {
        val character = Assistant(name = "only role")
        assertEquals(listOf(character.id), Settings(assistants = listOf(character)).withBuiltInDefaults().assistants.map { it.id })
        assertEquals(1, Settings(assistants = emptyList()).withBuiltInDefaults().assistants.size)
    }
}
