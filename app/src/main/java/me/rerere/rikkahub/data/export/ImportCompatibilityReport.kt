package me.rerere.rikkahub.data.export

import kotlinx.serialization.json.*
import me.rerere.rikkahub.data.ai.cards.SUPPORTED_SLASH_COMMANDS
import me.rerere.rikkahub.data.ai.cards.splitSlashScript

enum class ImportKind(val label: String) {
    CHARACTER("角色卡"), LOREBOOK("世界书 / 预设"), REGEX("正则脚本"), QUICK_REPLY("快捷回复")
}

enum class ImportFindingLevel(val label: String) {
    IMPORTED("已导入"), SKIPPED("已跳过"), UNSUPPORTED("暂不支持"), NOTICE("注意"), ERROR("失败")
}

data class ImportFinding(val level: ImportFindingLevel, val description: String)

data class ImportCompatibilityReport(
    val kind: ImportKind,
    val imported: Int,
    val skipped: Int = 0,
    val findings: List<ImportFinding> = emptyList(),
) {
    fun toPlainText(): String = buildString {
        appendLine("${kind.label}导入兼容性报告")
        appendLine("内容条目：导入 $imported 项，跳过 $skipped 项")
        findings.forEach { appendLine("[${it.level.label}] ${it.description}") }
        append("报告仅覆盖已识别的功能，未列出的第三方扩展不代表完整兼容。")
    }
}

/** Reports describe the real parser result; detecting a field never implies that it was imported. */
object ImportCompatibility {
    fun failure(kind: ImportKind, message: String?): ImportCompatibilityReport = ImportCompatibilityReport(
        kind, 0, findings = listOf(ImportFinding(ImportFindingLevel.ERROR, message?.take(500) ?: "无法识别或读取文件"))
    )

    fun inspect(kind: ImportKind, source: String, imported: Int): ImportCompatibilityReport {
        val element = runCatching { Json.parseToJsonElement(source) }.getOrNull()
            ?: return failure(kind, "JSON 格式无效")
        val root = element as? JsonObject
        val data = root?.get("data") as? JsonObject ?: root
        val issues = mutableListOf(if (imported > 0) {
            ImportFinding(ImportFindingLevel.IMPORTED, "${kind.label}：$imported 项解析成功")
        } else if (kind == ImportKind.LOREBOOK) {
            ImportFinding(ImportFindingLevel.NOTICE, "文件已导入，不含内容条目")
        } else {
            ImportFinding(ImportFindingLevel.ERROR, "未找到可导入内容，请查看以下跳过原因")
        })
        fun note(level: ImportFindingLevel, text: String) { issues += ImportFinding(level, text) }
        var candidates = imported
        when (kind) {
            ImportKind.CHARACTER -> {
                note(ImportFindingLevel.NOTICE, "已识别格式：${root?.text("spec") ?: if (root?.containsKey("data") == true) "V2 兼容结构" else "平铺 / 兼容角色格式"}")
                val mappedFields = mapOf("name" to "名称", "description" to "人物描述", "personality" to "性格",
                    "scenario" to "场景", "first_mes" to "开场白", "alternate_greetings" to "备用开场白",
                    "mes_example" to "示例对话", "system_prompt" to "系统提示词", "character_book" to "内嵌世界书")
                    .filterKeys { data?.containsKey(it) == true }.values
                if (mappedFields.isNotEmpty()) note(ImportFindingLevel.IMPORTED, "已映射字段：${mappedFields.joinToString()}")
                val book = data?.get("character_book") as? JsonObject
                inspectWorldEntries(values(book?.get("entries")), issues)
                val extensions = data?.get("extensions") as? JsonObject
                val unknownExtensions = extensions?.keys.orEmpty() - setOf("regex_scripts", "depth_prompt")
                if (unknownExtensions.isNotEmpty()) note(ImportFindingLevel.NOTICE,
                    "第三方扩展字段可能仅保留原始数据，不保证执行：${unknownExtensions.sorted().joinToString()}")
                inspectRegex(values(extensions?.get("regex_scripts")), issues)
                val standardCard = root?.text("spec")?.startsWith("chara_card") == true || data?.containsKey("name") == true
                val unknown = if (standardCard) data?.keys.orEmpty() - CHARACTER_FIELDS else emptySet()
                if (unknown.isNotEmpty()) note(ImportFindingLevel.SKIPPED, "未映射的角色字段：${unknown.sorted().joinToString()}")
                if (!standardCard) note(ImportFindingLevel.NOTICE, "兼容格式已转换为角色；源平台专有字段不保证全部保留")
                val text = source
                listOf("generateRaw", "eventEmit", "SillyTavern.getContext").filter { it in text }.forEach {
                    note(ImportFindingLevel.UNSUPPORTED, "检测到 $it 引用，当前 HTML 卡桥接未实现该接口")
                }
            }
            ImportKind.LOREBOOK -> {
                val globalOptions = root?.keys.orEmpty().intersect(setOf("scan_depth", "token_budget", "recursive_scanning", "max_recursion_steps"))
                if (globalOptions.isNotEmpty()) note(ImportFindingLevel.SKIPPED,
                    "世界书全局参数未应用，沿用本地设置：${globalOptions.sorted().joinToString()}")
                val prompts = root?.get("prompts") as? JsonArray
                if (prompts != null) {
                    val markerCount = prompts.count { (it as? JsonObject)?.bool("marker") == true }
                    candidates = prompts.size - markerCount
                    if (markerCount > 0) note(ImportFindingLevel.NOTICE, "$markerCount 个上下文占位标记由应用组装，不作为独立提示词导入")
                    val sampler = root?.keys.orEmpty().intersect(SAMPLER_FIELDS)
                    if (sampler.isNotEmpty()) note(ImportFindingLevel.SKIPPED,
                        "预设采样参数未应用，沿用当前模型设置：${sampler.sorted().joinToString()}")
                } else {
                    val entries = values(data?.get("entries"))
                    if (entries.isNotEmpty()) candidates = entries.size
                    inspectWorldEntries(entries, issues)
                    val sampler = root?.keys.orEmpty().intersect(SAMPLER_FIELDS)
                    if (sampler.isNotEmpty()) note(ImportFindingLevel.SKIPPED,
                        "预设采样参数未应用，沿用当前模型设置：${sampler.sorted().joinToString()}")
                }
                inspectRegex(values((root?.get("extensions") as? JsonObject)?.get("regex_scripts")), issues)
                val otherExtensions = (root?.get("extensions") as? JsonObject)?.keys.orEmpty() - "regex_scripts"
                if (otherExtensions.isNotEmpty()) note(ImportFindingLevel.SKIPPED,
                    "预设第三方扩展未导入：${otherExtensions.sorted().joinToString()}")
            }
            ImportKind.REGEX -> {
                val entries = if (element is JsonArray) element.toList() else listOf(element)
                candidates = entries.size
                inspectRegex(entries, issues)
            }
            ImportKind.QUICK_REPLY -> {
                val setOptions = root?.keys.orEmpty() - setOf("version", "name", "qrList", "quickReplies")
                if (setOptions.isNotEmpty()) note(ImportFindingLevel.SKIPPED,
                    "快捷回复集合设置未导入：${setOptions.sorted().joinToString()}")
                val entries = if (element is JsonArray) element.toList() else values(root?.get("qrList") ?: root?.get("quickReplies"))
                candidates = entries.size
                entries.forEachIndexed { index, item ->
                    val obj = item as? JsonObject
                    if (obj == null) {
                        note(ImportFindingLevel.SKIPPED, "快捷回复 #${index + 1}：不是有效对象")
                        return@forEachIndexed
                    }
                    val message = obj.text("message")
                    if (message.isNullOrBlank()) note(ImportFindingLevel.SKIPPED, "快捷回复 #${index + 1}：缺少有效 message")
                    else {
                        val unknown = splitSlashScript(message).filter { it.startsWith('/') }
                            .map { it.drop(1).trim().substringBefore(' ').substringBefore('\t').lowercase() }
                            .filter { it !in SUPPORTED_SLASH_COMMANDS }.distinct()
                        if (unknown.isNotEmpty()) note(ImportFindingLevel.UNSUPPORTED,
                            "快捷回复「${obj.text("label") ?: obj.text("title") ?: "#${index + 1}"}」已保留，命令不会执行：${unknown.joinToString { "/$it" }}")
                        val automation = obj.keys.intersect(setOf("executeOnStartup", "executeOnUser", "executeOnAi", "executeOnChatChange", "executeOnGroupMemberDraft"))
                            .filter { obj.bool(it) }
                        if (automation.isNotEmpty()) note(ImportFindingLevel.UNSUPPORTED, "快捷回复 #${index + 1} 的自动触发设置未应用：${automation.joinToString()}")
                        val otherOptions = obj.keys - setOf("id", "label", "title", "message", "isHidden", "executeOnStartup", "executeOnUser", "executeOnAi", "executeOnChatChange", "executeOnGroupMemberDraft")
                        if (otherOptions.isNotEmpty()) note(ImportFindingLevel.SKIPPED,
                            "快捷回复 #${index + 1} 的其他设置未导入：${otherOptions.sorted().joinToString()}")
                    }
                }
            }
        }
        val skipped = (candidates - imported).coerceAtLeast(0)
        if (skipped > 0) note(ImportFindingLevel.SKIPPED, "$skipped 项未被解析器导入（可能为空、格式无效或未进入生效顺序）")
        return ImportCompatibilityReport(kind, imported, skipped, issues.distinct())
    }

    private fun inspectWorldEntries(entries: List<JsonElement>, issues: MutableList<ImportFinding>) {
        entries.forEachIndexed { i, item ->
            val entry = item as? JsonObject ?: return@forEachIndexed
            val extensions = entry["extensions"] as? JsonObject
            val label = entry.text("comment") ?: entry.text("name") ?: "#${i + 1}"
            if (entry.bool("vectorized") || extensions?.bool("vectorized") == true) issues += ImportFinding(
                ImportFindingLevel.UNSUPPORTED, "世界书「$label」的向量检索激活未实现；已有关键词仍可匹配")
            if (!(entry.text("automationId") ?: extensions?.text("automation_id")).isNullOrBlank()) issues += ImportFinding(
                ImportFindingLevel.UNSUPPORTED, "世界书「$label」的自动化 ID 不会触发外部脚本")
            val position = (entry["position"] as? JsonPrimitive)?.intOrNull
            if (position != null && position !in 0..6) issues += ImportFinding(
                ImportFindingLevel.NOTICE, "世界书「$label」的未知插入位置 $position 使用解析器默认位置")
        }
    }

    private fun inspectRegex(entries: List<JsonElement>, issues: MutableList<ImportFinding>) {
        entries.forEachIndexed { i, item ->
            val entry = item as? JsonObject
            if (entry == null || entry.text("scriptName") == null || entry.text("findRegex").isNullOrBlank()) {
                issues += ImportFinding(ImportFindingLevel.SKIPPED, "正则 #${i + 1}：缺少 scriptName 或非空 findRegex")
                return@forEachIndexed
            }
            val label = entry.text("scriptName") ?: "#${i + 1}"
            val placement = (entry["placement"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }.orEmpty()
            if (placement.isEmpty()) issues += ImportFinding(ImportFindingLevel.NOTICE,
                "正则「$label」未指定位置；当前解析器映射为用户及助手消息，与酒馆的空位置行为不同")
            if (placement.any { it !in setOf(1, 2) }) issues += ImportFinding(ImportFindingLevel.UNSUPPORTED,
                "正则「$label」包含未支持的作用位置：${placement.filter { it !in setOf(1, 2) }.joinToString()}")
            val unsupported = entry.keys.intersect(setOf("trimStrings", "substituteRegex", "runOnEdit"))
                .filter { key -> entry[key] !in listOf(JsonNull, JsonPrimitive(false), JsonPrimitive(0)) && entry[key] != JsonArray(emptyList()) }
            if (unsupported.isNotEmpty()) issues += ImportFinding(ImportFindingLevel.SKIPPED,
                "正则「$label」以下设置未导入：${unsupported.joinToString()}")
            val otherOptions = entry.keys - setOf("id", "scriptName", "findRegex", "replaceString", "placement", "disabled", "markdownOnly", "promptOnly", "minDepth", "maxDepth", "trimStrings", "substituteRegex", "runOnEdit")
            if (otherOptions.isNotEmpty()) issues += ImportFinding(ImportFindingLevel.SKIPPED,
                "正则「$label」的其他字段未导入：${otherOptions.sorted().joinToString()}")
        }
    }

    private fun values(value: JsonElement?): List<JsonElement> = when (value) {
        is JsonArray -> value.toList()
        is JsonObject -> value.values.toList()
        else -> emptyList()
    }
    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.bool(key: String): Boolean = (get(key) as? JsonPrimitive)?.booleanOrNull == true
    private val SAMPLER_FIELDS = setOf("temperature", "top_p", "top_k", "min_p", "frequency_penalty", "presence_penalty", "repetition_penalty", "openai_max_tokens", "max_tokens", "seed")
    private val CHARACTER_FIELDS = setOf("name", "description", "personality", "scenario", "first_mes", "mes_example", "creator_notes", "creator_notes_multilingual", "system_prompt", "post_history_instructions", "alternate_greetings", "character_book", "tags", "creator", "character_version", "extensions", "assets", "nickname", "group_only_greetings", "source", "creation_date", "modification_date", "spec", "spec_version")
}
