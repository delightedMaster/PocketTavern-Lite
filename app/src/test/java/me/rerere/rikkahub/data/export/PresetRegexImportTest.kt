package me.rerere.rikkahub.data.export

import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.withoutInvalidReferences
import me.rerere.rikkahub.data.datastore.allRegexesOf
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantRegex
import me.rerere.rikkahub.data.model.RegexGroup
import me.rerere.rikkahub.data.model.RegexGroupSource
import me.rerere.rikkahub.data.model.LorebookKind
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * 酒馆预设导入时把 extensions.regex_scripts 收进全局正则的回归。
 *
 * 预设里的脚本以前会被静默丢掉，现在导入预设应同时落到 Settings.regexGroups，
 * 并对所有角色卡生效（排在角色卡自己的正则之后）。
 */
class PresetRegexImportTest {
    private val preset = """
    {
      "prompts": [{"identifier":"main","name":"Main","content":"main","role":"system"}],
      "prompt_order": [{"character_id":100001,"order":[{"identifier":"main","enabled":true}]}],
      "extensions": {
        "regex_scripts": [
          {"id":"r1","scriptName":"状态栏","findRegex":"/<StatusPlaceHolderImpl\\/>/gsi","replaceString":"html","placement":[2],"markdownOnly":true,"promptOnly":false,"disabled":false},
          {"id":"r2","scriptName":"隐藏","findRegex":"/<StatusPlaceHolderImpl\\/>/gsi","replaceString":"","placement":[2],"markdownOnly":false,"promptOnly":true,"disabled":false}
        ]
      }
    }
    """.trimIndent()

    @Test
    fun `regex scripts are extracted from preset extensions`() {
        val regexes = SillyTavernRegexImporter.parseFromPreset(preset)
        assertEquals(2, regexes.size)
        assertTrue(regexes.any { it.name == "状态栏" && it.visualOnly })
        assertTrue(regexes.any { it.name == "隐藏" && it.promptOnly })
    }

    @Test
    fun `preset without extensions yields empty list`() {
        assertTrue(SillyTavernRegexImporter.parseFromPreset("""{"prompts":[]}""").isEmpty())
    }

    @Test
    fun `allRegexesOf puts assistant regexes before global ones`() {
        val assistantRegex = AssistantRegex(id = Uuid.random())
        val globalRegex = AssistantRegex(id = Uuid.random())
        val assistant = Assistant(id = Uuid.random(), regexes = listOf(assistantRegex))
        val settings = Settings(regexGroups = listOf(RegexGroup(regexes = listOf(globalRegex))))

        assertEquals(listOf(assistantRegex, globalRegex), settings.allRegexesOf(assistant))
        assertEquals(listOf(globalRegex), settings.allRegexesOf(null))
    }

    @Test
    fun `preset and bundled regexes survive a single settings update and reload`() {
        val imported = LorebookImport.parse(preset, "写作预设")
        val updated = imported.applyTo(Settings(assistants = listOf(Assistant())))
        val restored = JsonInstant.decodeFromString<Settings>(JsonInstant.encodeToString(updated))
        assertEquals(LorebookKind.PRESET, restored.lorebooks.single().kind)
        assertEquals(1, restored.lorebooks.single().entries.size)
        assertEquals("写作预设", restored.regexGroups.single().name)
        assertEquals(2, restored.regexGroups.single().regexes.size)
        assertTrue(imported.report.toPlainText().contains("预设附带正则：导入 2 条，跳过 0 条"))
    }

    @Test
    fun `a malformed regex does not discard valid neighboring scripts or their disabled state`() {
        val mixed = preset.replace("\"regex_scripts\": [", """"regex_scripts": [
            {"scriptName":"损坏","findRegex":{}},
            {"scriptName":"停用规则","findRegex":"hello","disabled":true},
        """.trimIndent())
        val imported = LorebookImport.parse(mixed, "mixed")
        val regexes = imported.regexGroup!!.regexes
        assertEquals(3, regexes.size)
        assertEquals(false, regexes.single { it.name == "停用规则" }.enabled)
        assertTrue(imported.report.toPlainText().contains("导入 3 条，跳过 1 条"))
    }

    @Test
    fun `same named import preserves existing regex groups and unrelated settings`() {
        val original = RegexGroup(name = "写作预设", source = RegexGroupSource.PRESET, regexes = listOf(AssistantRegex(id = Uuid.random(), name = "原规则")))
        val settings = Settings(regexGroups = listOf(original))
        val imported = LorebookImport.parse(preset, "写作预设")
        val result = imported.applyTo(settings)
        assertEquals(original, result.regexGroups.first())
        assertEquals(2, result.regexGroups.size)
        assertEquals(settings.assistants, result.assistants)
    }

    @Test
    fun `preset without scripts does not add empty regex groups`() {
        val imported = LorebookImport.parse("""{"main_prompt":"write"}""", "plain")
        assertEquals(null, imported.regexGroup)
        assertTrue(imported.applyTo(Settings()).regexGroups.isEmpty())
    }

    @Test
    fun `legacy preset kind migrates without changing entries and remains after renaming description`() {
        val original = LorebookSerializer.importFromJson(preset, "old").copy(kind = LorebookKind.WORLD_BOOK)
        val migrated = Settings(lorebooks = listOf(original)).withoutInvalidReferences().lorebooks.single()
        assertEquals(original.id, migrated.id)
        assertEquals(original.entries, migrated.entries)
        assertEquals(LorebookKind.PRESET, migrated.kind)
        val edited = migrated.copy(description = "自己的说明")
        assertEquals(LorebookKind.PRESET, LorebookSerializer.importFromJson(LorebookSerializer.exportToJson(edited), "edited").kind)
    }

}
