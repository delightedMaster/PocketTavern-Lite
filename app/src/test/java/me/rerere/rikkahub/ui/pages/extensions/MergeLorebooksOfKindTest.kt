package me.rerere.rikkahub.ui.pages.extensions

import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.LorebookKind
import org.junit.Assert.assertEquals
import org.junit.Test

class MergeLorebooksOfKindTest {
    private fun world(name: String) = Lorebook(name = name, kind = LorebookKind.WORLD_BOOK)
    private fun preset(name: String) = Lorebook(name = name, kind = LorebookKind.PRESET)

    @Test
    fun reorder_within_kind_keeps_other_kind_positions() {
        val w1 = world("W1")
        val p1 = preset("P1")
        val w2 = world("W2")
        val all = listOf(w1, p1, w2)

        val merged = mergeLorebooksOfKind(all, LorebookKind.WORLD_BOOK, listOf(w2, w1))

        assertEquals(listOf("W2", "P1", "W1"), merged.map { it.name })
        assertEquals(LorebookKind.PRESET, merged[1].kind)
    }

    @Test
    fun delete_removes_slot_and_keeps_other_kind() {
        val w1 = world("W1")
        val p1 = preset("P1")
        val w2 = world("W2")
        val all = listOf(w1, p1, w2)

        // Positional slots: W2 fills the first world-book slot, the last slot is dropped.
        val merged = mergeLorebooksOfKind(all, LorebookKind.WORLD_BOOK, listOf(w2))

        assertEquals(listOf("W2", "P1"), merged.map { it.name })
        assertEquals(1, merged.indexOfFirst { it.kind == LorebookKind.PRESET })
    }

    @Test
    fun add_appends_new_items_at_end() {
        val w1 = world("W1")
        val p1 = preset("P1")
        val all = listOf(w1, p1)
        val added = world("W2")

        val merged = mergeLorebooksOfKind(all, LorebookKind.WORLD_BOOK, listOf(w1, added))

        assertEquals(listOf("W1", "P1", "W2"), merged.map { it.name })
    }

    @Test
    fun other_kind_untouched_when_editing_its_own_kind() {
        val w1 = world("W1")
        val p1 = preset("P1")
        val p2 = preset("P2")
        val all = listOf(p1, w1, p2)

        val edited = w1.copy(name = "W1-edited")
        val merged = mergeLorebooksOfKind(all, LorebookKind.WORLD_BOOK, listOf(edited))

        assertEquals(listOf("P1", "W1-edited", "P2"), merged.map { it.name })
        assertEquals(listOf(p1, p2), merged.filter { it.kind == LorebookKind.PRESET })
    }

    @Test
    fun empty_updated_list_removes_all_of_kind() {
        val all = listOf(world("W1"), preset("P1"), world("W2"))

        val merged = mergeLorebooksOfKind(all, LorebookKind.WORLD_BOOK, emptyList())

        assertEquals(listOf("P1"), merged.map { it.name })
    }
}
