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

    @Test
    fun presetSelectionIsMutuallyExclusiveAndKeepsWorldBooks() {
        val world1 = Lorebook(name = "world1", kind = LorebookKind.WORLD_BOOK)
        val world2 = Lorebook(name = "world2", kind = LorebookKind.WORLD_BOOK)
        val presetA = Lorebook(name = "presetA", kind = LorebookKind.PRESET)
        val presetB = Lorebook(name = "presetB", kind = LorebookKind.PRESET)
        val all = listOf(world1, world2, presetA, presetB)

        val initial = setOf(world1.id, presetA.id)
        // 切换到预设 B：自动取消预设 A，世界书 1 保留
        val switched = toggleLorebookBinding(initial, presetB.id, checked = true, LorebookKind.PRESET, all)
        assertEquals(setOf(world1.id, presetB.id), switched)

        // 世界书保持多选，不影响已选预设 B
        val withWorld2 = toggleLorebookBinding(switched, world2.id, checked = true, LorebookKind.WORLD_BOOK, all)
        assertEquals(setOf(world1.id, world2.id, presetB.id), withWorld2)

        // 允许关闭当前预设
        val cleared = toggleLorebookBinding(withWorld2, presetB.id, checked = false, LorebookKind.PRESET, all)
        assertEquals(setOf(world1.id, world2.id), cleared)
    }
}
