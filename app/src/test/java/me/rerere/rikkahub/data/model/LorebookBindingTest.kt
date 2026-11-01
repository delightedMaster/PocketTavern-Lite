package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

class LorebookBindingTest {
    @Test
    fun tabsSplitWorldBooksAndPresets() {
        val world = Lorebook(name = "world", kind = LorebookKind.WORLD_BOOK)
        val preset = Lorebook(name = "preset", kind = LorebookKind.PRESET)
        val legacy = Lorebook(name = "legacy")
        val all = listOf(world, preset, legacy)

        assertEquals(LorebookKind.WORLD_BOOK, legacy.kind)
        assertEquals(listOf(world, legacy), lorebooksOfKind(all, LorebookKind.WORLD_BOOK))
        assertEquals(listOf(preset), lorebooksOfKind(all, LorebookKind.PRESET))
    }

    @Test
    fun togglingOneKindKeepsTheOtherKindBound() {
        val worldId = Uuid.random()
        val presetId = Uuid.random()
        val bound = setOf(worldId, presetId)

        assertEquals(setOf(presetId), toggleLorebookBinding(bound, worldId, checked = false))
        assertEquals(setOf(worldId), toggleLorebookBinding(bound, presetId, checked = false))
        assertEquals(bound, toggleLorebookBinding(setOf(worldId), presetId, checked = true))
        assertEquals(bound, toggleLorebookBinding(setOf(presetId), worldId, checked = true))
    }
}
