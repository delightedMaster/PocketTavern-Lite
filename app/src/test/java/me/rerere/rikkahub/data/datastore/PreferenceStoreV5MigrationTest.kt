package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.ai.cards.DEFAULT_HTML_CARD_ALLOWLIST
import me.rerere.rikkahub.data.datastore.migration.migrateHtmlCardAllowlist
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferenceStoreV5MigrationTest {
    private fun migrate(json: String?): List<String> =
        JsonInstant.decodeFromString(migrateHtmlCardAllowlist(json))

    @Test
    fun `missing or empty allowlist gets the default CDNs`() {
        assertEquals(DEFAULT_HTML_CARD_ALLOWLIST, migrate(null))
        assertEquals(DEFAULT_HTML_CARD_ALLOWLIST, migrate("[]"))
        assertEquals(DEFAULT_HTML_CARD_ALLOWLIST, migrate("not json"))
    }

    @Test
    fun `hosts the user entered are kept`() {
        assertEquals(listOf("example.com"), migrate("""["example.com"]"""))
    }
}
