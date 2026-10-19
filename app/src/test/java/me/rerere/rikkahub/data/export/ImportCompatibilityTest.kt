package me.rerere.rikkahub.data.export

import org.junit.Assert.*
import org.junit.Test

class ImportCompatibilityTest {
    @Test
    fun `an empty native lorebook remains a valid import`() {
        val json = LorebookSerializer.exportToJson(me.rerere.rikkahub.data.model.Lorebook(name = "blank"))
        val book = LorebookSerializer.importFromJson(json, "blank")
        val report = ImportCompatibility.inspect(ImportKind.LOREBOOK, json, book.entries.size)
        assertEquals(0, report.imported)
        assertFalse(report.findings.any { it.level == ImportFindingLevel.ERROR })
    }

    @Test
    fun `partly supported quick replies preserve text and report commands plus skipped items`() {
        val json = """{"name":"set","qrList":[{"label":"write","message":"/send hello | /if x","executeOnStartup":true},{"message":""},null]}"""
        val messages = SillyTavernQuickRepliesImporter.parse(json)
        val report = ImportCompatibility.inspect(ImportKind.QUICK_REPLY, json, messages.size)
        assertEquals(1, report.imported)
        assertEquals(2, report.skipped)
        assertEquals("/send hello | /if x", messages.single().content)
        assertTrue(report.findings.any { it.level == ImportFindingLevel.UNSUPPORTED && "/if" in it.description })
        assertTrue(report.findings.any { "自动触发" in it.description })
    }

    @Test
    fun `preset markers do not inflate skipped count and sampling settings are explicitly ignored`() {
        val json = """{"temperature":0.5,"prompts":[{"identifier":"main","name":"main","content":"story"},{"identifier":"chatHistory","marker":true},{"identifier":"empty","content":""}],"prompt_order":[{"character_id":100001,"order":[{"identifier":"main","enabled":true},{"identifier":"chatHistory","enabled":true},{"identifier":"empty","enabled":true}]}]}"""
        val book = LorebookSerializer.importFromJson(json, "preset")
        val report = ImportCompatibility.inspect(ImportKind.LOREBOOK, json, book.entries.size)
        assertEquals(1, report.imported)
        assertEquals(1, report.skipped)
        assertTrue(report.findings.any { "temperature" in it.description && it.level == ImportFindingLevel.SKIPPED })
        assertTrue(report.findings.any { "占位标记" in it.description })
    }

    @Test
    fun `regex partial import explains empty placements and unimplemented settings`() {
        val json = """[{"scriptName":"card","findRegex":"hello","placement":[],"trimStrings":[" "]},{"scriptName":"invalid"}]"""
        val parsed = SillyTavernRegexImporter.parse(json)
        val report = ImportCompatibility.inspect(ImportKind.REGEX, json, parsed.size)
        assertEquals(1, report.imported)
        assertEquals(1, report.skipped)
        assertTrue(report.findings.any { "空位置行为不同" in it.description })
        assertTrue(report.findings.any { "trimStrings" in it.description })
    }

    @Test
    fun `character report separates preserved extension data from executable compatibility`() {
        val json = """{"spec":"chara_card_v3","data":{"name":"hero","creator_notes_multilingual":{"en":"notes"},"extensions":{"unknown_plugin":{"script":"generateRaw()"}},"character_book":{"entries":[{"name":"lore","vectorized":true}]}}}"""
        val report = ImportCompatibility.inspect(ImportKind.CHARACTER, json, 1)
        assertTrue(report.findings.any { "unknown_plugin" in it.description && it.level == ImportFindingLevel.NOTICE })
        assertTrue(report.findings.any { "generateRaw" in it.description && it.level == ImportFindingLevel.UNSUPPORTED })
        assertTrue(report.findings.any { "向量检索" in it.description })
        assertFalse(report.findings.any { "未映射" in it.description && "creator_notes_multilingual" in it.description })
    }

    @Test
    fun `invalid JSON is a failure not a successful empty import`() {
        val report = ImportCompatibility.inspect(ImportKind.CHARACTER, "{bad", 1)
        assertEquals(0, report.imported)
        assertTrue(report.findings.single().level == ImportFindingLevel.ERROR)
        assertTrue(report.toPlainText().contains("JSON 格式无效"))
    }
}
