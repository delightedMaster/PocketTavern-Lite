package me.rerere.rikkahub.data.export

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.RegexGroup
import me.rerere.rikkahub.data.model.RegexGroupSource

/** Parse and persist a book/preset and its bundled regex scripts as a single import. */
data class LorebookImport(
    val book: Lorebook,
    val regexGroup: RegexGroup?,
    val report: ImportCompatibilityReport,
) {
    fun applyTo(settings: Settings): Settings = settings.copy(
        lorebooks = settings.lorebooks + book,
        // Do not replace a different preset's scripts just because its filename is identical.
        regexGroups = settings.regexGroups + listOfNotNull(regexGroup),
    )

    companion object {
        fun parse(json: String, fileName: String?): LorebookImport {
            val book = LorebookSerializer.importFromJson(json, fileName)
            val root = Json.parseToJsonElement(json) as? JsonObject
            val scripts = (root?.get("extensions") as? JsonObject)?.get("regex_scripts")
            val regexes = SillyTavernRegexImporter.parseFromPreset(json)
            val group = regexes.takeIf { it.isNotEmpty() }?.let {
                RegexGroup(name = book.name, source = RegexGroupSource.PRESET, regexes = it)
            }
            val baseReport = ImportCompatibility.inspect(ImportKind.LOREBOOK, json, book.entries.size)
            val findings = when (scripts) {
                null -> emptyList()
                is JsonArray -> buildList {
                    add(ImportFinding(if (regexes.isEmpty()) ImportFindingLevel.SKIPPED else ImportFindingLevel.IMPORTED, "预设附带正则：导入 ${regexes.size} 条，跳过 ${scripts.size - regexes.size} 条；保留原有启用状态"))
                    if (scripts.size > regexes.size) {
                        add(ImportFinding(ImportFindingLevel.SKIPPED, "部分正则缺少必要字段或格式无效；有效规则已保留，请检查原文件"))
                    }
                }
                else -> listOf(ImportFinding(ImportFindingLevel.SKIPPED, "预设附带正则的 regex_scripts 不是数组，无法导入"))
            }
            return LorebookImport(book, group, baseReport.copy(findings = baseReport.findings + findings))
        }
    }
}
