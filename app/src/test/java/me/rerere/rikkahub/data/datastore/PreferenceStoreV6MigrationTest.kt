package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.datastore.migration.migrateDisplaySettingQuoteHighlight
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceStoreV6MigrationTest {
    @Test
    fun `legacy display setting with persisted false quote highlight migrates to true`() {
        val legacyJson = """{"enableLatexRendering":true,"enableQuoteHighlight":false}"""
        val migrated = JsonInstant.decodeFromString<DisplaySetting>(
            migrateDisplaySettingQuoteHighlight(legacyJson)
        )

        assertTrue(migrated.enableQuoteHighlight)
        assertTrue(migrated.enableParenthesisHighlight)
    }

    @Test
    fun `new display setting that already has parenthesis toggle preserves user choice`() {
        val modernJson = """{"enableQuoteHighlight":false,"enableParenthesisHighlight":true}"""
        val migrated = JsonInstant.decodeFromString<DisplaySetting>(
            migrateDisplaySettingQuoteHighlight(modernJson)
        )

        assertFalse(migrated.enableQuoteHighlight)
        assertTrue(migrated.enableParenthesisHighlight)
    }

    @Test
    fun `invalid json is returned unchanged`() {
        assertEquals("not json", migrateDisplaySettingQuoteHighlight("not json"))
    }
}
