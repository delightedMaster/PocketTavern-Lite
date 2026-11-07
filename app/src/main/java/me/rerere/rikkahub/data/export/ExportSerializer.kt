package me.rerere.rikkahub.data.export

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.AssistantRegex
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.LorebookKind
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.data.model.StPresetSettings
import me.rerere.rikkahub.data.model.StPromptMeta
import me.rerere.rikkahub.ui.pages.assistant.detail.mapSelectiveLogic
import me.rerere.rikkahub.ui.pages.assistant.detail.parseDelayUntilRecursionInt
import me.rerere.rikkahub.ui.pages.assistant.detail.mapTavernRole
import me.rerere.rikkahub.utils.toLocalString
import java.time.LocalDateTime
import kotlin.uuid.Uuid

@Serializable
data class ExportData(
    val version: Int = 1,
    val type: String,
    val data: JsonElement
)

interface ExportSerializer<T> {
    val type: String

    fun export(data: T): ExportData
    fun import(context: Context, uri: Uri): Result<T>

    // 获取导出文件名
    fun getExportFileName(data: T): String = "${type}.json"

    // 便捷方法：直接导出为 JSON 字符串
    fun exportToJson(data: T, json: Json = DefaultJson): String {
        return json.encodeToString(ExportData.serializer(), export(data))
    }

    // 读取 URI 内容的便捷方法
    fun readUri(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Failed to read file")
    }

    fun getUriFileName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) cursor.getString(nameIndex) else null
            } else null
        }
    }

    companion object {
        val DefaultJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }
    }
}

object ModeInjectionSerializer : ExportSerializer<PromptInjection.ModeInjection> {
    override val type = "mode_injection"

    override fun getExportFileName(data: PromptInjection.ModeInjection): String {
        return "${data.name.ifEmpty { type }}.json"
    }

    override fun export(data: PromptInjection.ModeInjection): ExportData {
        return ExportData(
            type = type,
            data = ExportSerializer.DefaultJson.encodeToJsonElement(data)
        )
    }

    override fun import(context: Context, uri: Uri): Result<PromptInjection.ModeInjection> {
        return runCatching {
            val json = readUri(context, uri)
            // 首先尝试解析为自己的格式
            tryImportNative(json)
                ?: throw IllegalArgumentException("Unsupported format")
        }
    }

    private fun tryImportNative(json: String): PromptInjection.ModeInjection? {
        return runCatching {
            val exportData = ExportSerializer.DefaultJson.decodeFromString(
                ExportData.serializer(),
                json
            )
            if (exportData.type != type) return null
            ExportSerializer.DefaultJson
                .decodeFromJsonElement<PromptInjection.ModeInjection>(exportData.data)
                .copy(id = Uuid.random())
        }.getOrNull()
    }
}

object LorebookSerializer : ExportSerializer<Lorebook> {
    override val type = "lorebook"

    override fun getExportFileName(data: Lorebook): String {
        return "${data.name.ifEmpty { type }}.json"
    }

    override fun export(data: Lorebook): ExportData {
        return ExportData(
            type = type,
            data = ExportSerializer.DefaultJson.encodeToJsonElement(data)
        )
    }

    override fun import(context: Context, uri: Uri): Result<Lorebook> {
        return runCatching {
            importFromJson(readUri(context, uri), getUriFileName(context, uri)?.removeSuffix(".json"))
        }
    }

    /**
     * 从已读取的 JSON 解析世界书/酒馆预设。[fileName] 用于给导入的书命名。
     * 供导入 UI 复用：同一次读取里还要顺带抽预设的正则脚本，免得重复读 URI。
     */
    fun importFromJson(json: String, fileName: String?): Lorebook {
        // 首先尝试解析为自己的格式
        return tryImportNative(json)
            // 然后尝试解析为 SillyTavern 世界书
            ?: tryImportSillyTavern(json, fileName)
            // 酒馆 Chat Completion 预设（main_prompt/jailbreak_prompt 等）→ 转成 constant 条目的世界书
            ?: tryImportSillyTavernPreset(json, fileName)
            // 正则脚本不在这里导入，给出明确指引而不是静默生成空世界书
            ?: throwSillyTavernRegexScriptError(json)
            ?: throw IllegalArgumentException("Unsupported format")
    }

    private fun tryImportNative(json: String): Lorebook? {
        return runCatching {
            val exportData = ExportSerializer.DefaultJson.decodeFromString(
                ExportData.serializer(),
                json
            )
            if (exportData.type != type) return null
            ExportSerializer.DefaultJson
                .decodeFromJsonElement<Lorebook>(exportData.data)
                .copy(
                    id = Uuid.random(),
                    entries = ExportSerializer.DefaultJson
                        .decodeFromJsonElement<Lorebook>(exportData.data)
                        .entries.map { it.copy(id = Uuid.random()) }
                )
        }.getOrNull()
    }

    private fun tryImportSillyTavern(json: String, fileName: String?): Lorebook? {
        return runCatching {
            val stLorebook = ExportSerializer.DefaultJson.decodeFromString(
                SillyTavernLorebook.serializer(),
                json
            )
            // entries 有默认值，预设/正则脚本等其他 JSON 会"成功"解码成空世界书，
            // 必须判空让后续格式识别接管
            if (stLorebook.entries.isEmpty()) return null
            Lorebook(
                id = Uuid.random(),
                name = fileName ?: LocalDateTime.now().toLocalString(),
                description = "",
                enabled = true,
                entries = stLorebook.entries.values.map { entry ->
                    PromptInjection.RegexInjection(
                        id = Uuid.random(),
                        name = entry.comment.orEmpty().ifEmpty { entry.key.firstOrNull().orEmpty() },
                        enabled = !entry.disable,
                        priority = entry.order,
                        position = mapSillyTavernPosition(entry.position),
                        injectDepth = entry.depth,
                        content = entry.content,
                        keywords = entry.key,
                        secondaryKeys = entry.keysecondary,
                        useRegex = false, // 官方键始终支持 /regex/ 语法，keyMatches 会自动识别
                        caseSensitive = entry.caseSensitive ?: false,
                        matchWholeWords = entry.matchWholeWords ?: extBool(entry.extensions, "match_whole_words"),
                        excludeRecursion = entry.excludeRecursion ?: extBool(entry.extensions, "exclude_recursion"),
                        preventRecursion = entry.preventRecursion ?: extBool(entry.extensions, "prevent_recursion"),
                        delayUntilRecursion = parseDelayUntilRecursionInt(entry.delayUntilRecursion)
                            ?: parseDelayUntilRecursionInt(
                                entry.extensions?.jsonObject?.get("delay_until_recursion")
                            ) ?: 0,
                        scanDepth = entry.scanDepth,
                        constantActive = entry.constant,
                        selective = entry.selective,
                        selectiveLogic = mapSelectiveLogic(entry.selectiveLogic),
                        probability = entry.probability ?: 100,
                        useProbability = entry.useProbability ?: true,
                        group = entry.group.orEmpty(),
                        groupWeight = entry.groupWeight ?: 100,
                        groupOverride = entry.groupOverride ?: false,
                        role = mapTavernRole((entry.role as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: "0"),
                        sticky = entry.sticky ?: 0,
                        cooldown = entry.cooldown ?: 0,
                        delay = entry.delay ?: 0,
                        automationId = extString(entry.extensions, "automation_id"),
                        displayIndex = extInt(entry.extensions, "display_index"),
                        displayPosition = extInt(entry.extensions, "display_position"),
                        useGroupScoring = extBool(entry.extensions, "use_group_scoring"),
                        ignoreBudget = extBool(entry.extensions, "ignore_budget"),
                        // 官方 v2 世界书条目的顶层 vectorized 字段（向量检索激活）
                        vectorized = entry.vectorized ?: false,
                        triggers = extStringArray(entry.extensions, "triggers"),
                        matchPersonaDescription = extBool(entry.extensions, "match_persona_description"),
                        matchCharacterDescription = extBool(entry.extensions, "match_character_description"),
                        matchCharacterPersonality = extBool(entry.extensions, "match_character_personality"),
                        matchCharacterDepthPrompt = extBool(entry.extensions, "match_character_depth_prompt"),
                        matchScenario = extBool(entry.extensions, "match_scenario"),
                        matchCreatorNotes = extBool(entry.extensions, "match_creator_notes"),
                    )
                }
            )
        }.getOrNull()
    }

    /**
     * 酒馆 Chat Completion 预设 → constant 条目的世界书。
     * 两种格式：
     * - 新版：prompts 数组 + prompt_order（character_id 100001 是当前生效顺序），
     *   自定义条目 injection_position=0 为相对深度注入 → AT_DEPTH 条目
     * - 旧版：main_prompt/nsfw_prompt/jailbreak_prompt 平铺字段
     * 采样参数（temperature 等）本地由模型设置管理，忽略。
     */
    private fun tryImportSillyTavernPreset(json: String, fileName: String?): Lorebook? {
        return runCatching {
            val entries = tryImportPresetOrdered(json) ?: tryImportPresetFlat(json)
            if (entries == null || entries.isEmpty()) return null
            // 预设 JSON 自带的 name/description 优先；缺失或空白时回退到文件名/默认文案
            val root = runCatching { ExportSerializer.DefaultJson.parseToJsonElement(json) }.getOrNull() as? JsonObject
            val jsonName = root?.stringField("name")
            val jsonDescription = root?.stringField("description")
            Lorebook(
                id = Uuid.random(),
                name = jsonName ?: fileName ?: LocalDateTime.now().toLocalString(),
                description = jsonDescription ?: "SillyTavern 预设导入",
                kind = LorebookKind.PRESET,
                enabled = true,
                entries = entries,
                stPreset = if (entries.any { it.stPrompt != null }) tryImportPresetSettings(json) else null,
            )
        }.getOrNull()
    }

    /** 取顶层字符串字段，去掉首尾空白；缺失、非字符串或空白返回 null */
    private fun JsonObject.stringField(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * 新版预设（prompts + prompt_order）解析，保留酒馆 Prompt Manager 的完整结构：
     *
     * - 条目按 prompt_order 的顺序输出，marker（chatHistory、charDescription…）也作为条目保留，
     *   带 [StPromptMeta]，生成时由 assembleTavernPreset 按这个顺序组装。
     * - 开关只看 prompt_order 里的 enabled（PromptManager.js 只读 order 项，prompts[].enabled 不参与）。
     *   prompt_order 项缺 enabled 字段视为启用。
     * - injection_position：0 = 相对位置（按顺序原地排列），1 = 绝对位置（按 injection_depth 插进聊天记录）。
     * - prompts 里有、但没列进 prompt_order 的条目，酒馆不会发送；这里保留为禁用，用户仍可手动打开。
     *
     * [position] 仍按旧语义给出近似值（chatHistory 之前 → 系统区，之后 → 聊天末尾，绝对 → AT_DEPTH），
     * 只在不走有序组装的旧路径（如导出、旧界面）里使用。
     *
     * internal 供单测直接跑真实解析流程。
     */
    internal fun tryImportPresetOrdered(json: String): List<PromptInjection.RegexInjection>? {
        val obj = runCatching { ExportSerializer.DefaultJson.parseToJsonElement(json) }.getOrNull() as? JsonObject
        val promptsArray = obj?.get("prompts") as? kotlinx.serialization.json.JsonArray ?: return null
        if (promptsArray.isEmpty()) return null
        val preset = ExportSerializer.DefaultJson.decodeFromJsonElement(SillyTavernPresetOrdered.serializer(), obj)
        val promptsById = preset.prompts.associateBy { it.identifier }
        // 官方用 dummy character 100001 的顺序作为当前生效列表；缺失时取条目最多的
        val order = preset.promptOrder.firstOrNull { it.characterId == 100001L }?.order
            ?: preset.promptOrder.maxByOrNull { it.order.size }?.order
            ?: return null

        val chatHistoryIndex = order.indexOfFirst { it.identifier == "chatHistory" }

        fun roleOf(prompt: StPresetPrompt) = when (prompt.role?.lowercase()) {
            "user", "1" -> me.rerere.ai.core.MessageRole.USER
            "assistant", "2" -> me.rerere.ai.core.MessageRole.ASSISTANT
            else -> me.rerere.ai.core.MessageRole.SYSTEM
        }

        fun entryOf(prompt: StPresetPrompt, enabled: Boolean, afterHistory: Boolean) =
            PromptInjection.RegexInjection(
                id = Uuid.random(),
                name = prompt.name.ifBlank { prompt.identifier },
                enabled = enabled,
                position = when {
                    prompt.injectionPosition == 1 -> InjectionPosition.AT_DEPTH
                    prompt.identifier == "main" -> InjectionPosition.BEFORE_SYSTEM_PROMPT
                    afterHistory -> InjectionPosition.BOTTOM_OF_CHAT
                    else -> InjectionPosition.AFTER_SYSTEM_PROMPT
                },
                injectDepth = prompt.injectionDepth ?: 4,
                content = if (prompt.marker) "" else prompt.content,
                role = roleOf(prompt),
                constantActive = true, // 预设条目无条件注入
                stPrompt = StPromptMeta(
                    identifier = prompt.identifier,
                    marker = prompt.marker,
                    absolute = prompt.injectionPosition == 1,
                    injectionOrder = prompt.injectionOrder ?: 100,
                ),
            )

        val ordered = order.mapIndexedNotNull { index, entry ->
            val prompt = promptsById[entry.identifier] ?: return@mapIndexedNotNull null
            // 未启用条目保留为禁用（issue #5）；只丢弃「启用但没有内容」的普通条目，酒馆也会跳过它们
            if (!prompt.marker && prompt.content.isBlank() && entry.enabled) return@mapIndexedNotNull null
            entryOf(prompt, enabled = entry.enabled, afterHistory = chatHistoryIndex in 0 until index)
        }
        val orderedIds = order.map { it.identifier }.toSet()
        val extra = preset.prompts.mapNotNull { prompt ->
            if (prompt.identifier in orderedIds || prompt.marker) return@mapNotNull null
            if (prompt.content.isBlank() && prompt.enabled) return@mapNotNull null
            entryOf(prompt, enabled = false, afterHistory = false)
        }
        return ordered + extra
    }

    /** 预设里影响提示词结构的设置；不是新版有序预设时返回 null */
    internal fun tryImportPresetSettings(json: String): StPresetSettings? {
        val obj = runCatching { ExportSerializer.DefaultJson.parseToJsonElement(json) }.getOrNull() as? JsonObject
            ?: return null
        if (obj["prompts"] !is kotlinx.serialization.json.JsonArray || obj["prompt_order"] !is kotlinx.serialization.json.JsonArray) {
            return null
        }
        fun str(key: String) = (obj[key] as? JsonPrimitive)?.contentOrNull
        val defaults = StPresetSettings()
        return StPresetSettings(
            squashSystemMessages = (obj["squash_system_messages"] as? JsonPrimitive)?.booleanOrNull ?: false,
            scenarioFormat = str("scenario_format") ?: defaults.scenarioFormat,
            personalityFormat = str("personality_format") ?: defaults.personalityFormat,
            wiFormat = str("wi_format") ?: defaults.wiFormat,
        )
    }

    /** 旧版预设（main_prompt 平铺字段）解析 */
    private fun tryImportPresetFlat(json: String): List<PromptInjection.RegexInjection>? {
        val preset = runCatching {
            ExportSerializer.DefaultJson.decodeFromString(SillyTavernPresetFlat.serializer(), json)
        }.getOrNull() ?: return null
        if (preset.mainPrompt.isBlank() && preset.nsfwPrompt.isBlank() && preset.jailbreakPrompt.isBlank()) return null
        fun presetEntry(
            name: String,
            content: String,
            position: InjectionPosition,
            role: me.rerere.ai.core.MessageRole,
            enabled: Boolean = true,
        ) = PromptInjection.RegexInjection(
            id = Uuid.random(),
            name = name,
            enabled = enabled,
            position = position,
            content = content,
            role = role,
            constantActive = true,
        )
        return buildList {
                preset.mainPrompt.takeIf { it.isNotBlank() }?.let {
                    add(presetEntry("Main Prompt", it, InjectionPosition.BEFORE_SYSTEM_PROMPT, me.rerere.ai.core.MessageRole.SYSTEM))
                }
                preset.nsfwPrompt.takeIf { it.isNotBlank() }?.let {
                    // nsfw_toggle=false 时导入但默认关闭，用户可手动开
                    add(
                        presetEntry(
                            "NSFW Prompt",
                            it,
                            if (preset.nsfwFirst) InjectionPosition.BEFORE_SYSTEM_PROMPT else InjectionPosition.AFTER_SYSTEM_PROMPT,
                            me.rerere.ai.core.MessageRole.SYSTEM,
                            enabled = preset.nsfwToggle,
                        )
                    )
                }
                preset.jailbreakPrompt.takeIf { it.isNotBlank() }?.let {
                    add(
                        presetEntry(
                            "Jailbreak",
                            it,
                            InjectionPosition.BOTTOM_OF_CHAT, // 官方 post-history：聊天末尾
                            if (preset.jailbreakSystem) me.rerere.ai.core.MessageRole.SYSTEM else me.rerere.ai.core.MessageRole.USER,
                        )
                    )
                }
        }
    }

    /** 酒馆正则脚本走助手详情-消息正则导入，这里明确报错而不是生成空世界书 */
    private fun throwSillyTavernRegexScriptError(json: String): Lorebook? {
        val obj = runCatching { ExportSerializer.DefaultJson.parseToJsonElement(json) }.getOrNull() as? JsonObject
        if (obj != null && obj.containsKey("scriptName") && obj.containsKey("findRegex")) {
            throw IllegalArgumentException("检测到酒馆正则脚本，请在 角色卡详情 → 消息正则 区域导入")
        }
        return null
    }

    /** 官方 world_info_position：0=before 1=after 2=ANTop 3=ANBottom 4=atDepth 5=EMTop 6=EMBottom 7=outlet */
    private fun mapSillyTavernPosition(position: Int): InjectionPosition {
        return when (position) {
            0 -> InjectionPosition.BEFORE_CHARACTER
            1 -> InjectionPosition.AFTER_CHARACTER
            2 -> InjectionPosition.AUTHOR_NOTE   // ANTop
            3 -> InjectionPosition.AUTHOR_NOTE   // ANBottom
            4 -> InjectionPosition.AT_DEPTH
            5 -> InjectionPosition.EM_TOP
            6 -> InjectionPosition.EM_BOTTOM
            else -> InjectionPosition.AFTER_CHARACTER // outlet 暂不支持，落回角色卡后
        }
    }

    private fun extBool(extensions: JsonElement?, key: String): Boolean =
        extensions?.jsonObject?.get(key)?.jsonPrimitive?.booleanOrNull ?: false

    private fun extInt(extensions: JsonElement?, key: String): Int =
        extensions?.jsonObject?.get(key)?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0

    private fun extString(extensions: JsonElement?, key: String): String =
        extensions?.jsonObject?.get(key)?.jsonPrimitive?.contentOrNull ?: ""

    private fun extStringArray(extensions: JsonElement?, key: String): List<String> {
        val element = extensions?.jsonObject?.get(key) ?: return emptyList()
        return if (element is kotlinx.serialization.json.JsonArray) {
            element.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
        } else {
            (element as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        }
    }
}

@Serializable
private data class SillyTavernLorebook(
    val entries: Map<String, SillyTavernEntry> = emptyMap(),
)

/** 酒馆新版 Chat Completion 预设（prompts 数组 + prompt_order） */
@Serializable
private data class SillyTavernPresetOrdered(
    val prompts: List<StPresetPrompt> = emptyList(),
    @SerialName("prompt_order") val promptOrder: List<StPromptOrder> = emptyList(),
)

@Serializable
private data class StPresetPrompt(
    val identifier: String = "",
    val name: String = "",
    val content: String = "",
    val marker: Boolean = false,
    // 只对没列进 prompt_order 的条目有意义（酒馆 Prompt Manager 不读这个字段）
    val enabled: Boolean = true,
    val role: String? = null,
    @SerialName("injection_position") val injectionPosition: Int? = null,
    @SerialName("injection_depth") val injectionDepth: Int? = null,
    @SerialName("injection_order") val injectionOrder: Int? = null,
)

@Serializable
private data class StPromptOrder(
    @SerialName("character_id") val characterId: Long = 0,
    val order: List<StOrderEntry> = emptyList(),
)

@Serializable
private data class StOrderEntry(
    val identifier: String = "",
    // 酒馆语义：prompt_order 项缺 enabled 字段视为启用。
    // 默认 false 会把没写该字段的正常预设整条判为禁用。
    val enabled: Boolean = true,
)

/** 酒馆旧版 Chat Completion 预设（main_prompt 平铺字段） */
@Serializable
private data class SillyTavernPresetFlat(
    @SerialName("main_prompt") val mainPrompt: String = "",
    @SerialName("nsfw_prompt") val nsfwPrompt: String = "",
    @SerialName("nsfw_toggle") val nsfwToggle: Boolean = false,
    @SerialName("nsfw_first") val nsfwFirst: Boolean = false,
    @SerialName("jailbreak_prompt") val jailbreakPrompt: String = "",
    @SerialName("jailbreak_system") val jailbreakSystem: Boolean = true,
)

/**
 * 酒馆正则脚本（Regex Script）解析：
 * 单对象或数组（酒馆"导出全部"为数组），映射为 AssistantRegex。
 * placement：1=用户输入 2=AI输出 3=Slash命令 4=世界信息 5=推理（3-5 本地不适用，忽略）；
 * markdownOnly=仅影响显示 → visualOnly；promptOnly=仅影响发送给 AI 的提示 → promptOnly；
 * 两者都未勾选（酒馆默认）= 显示与提示词两层都生效。
 */
object SillyTavernRegexImporter {
    fun parse(json: String): List<AssistantRegex> = runCatching {
        when (val element = kotlinx.serialization.json.Json.parseToJsonElement(json)) {
            is kotlinx.serialization.json.JsonArray -> element.filterIsInstance<JsonObject>()
            is JsonObject -> listOf(element)
            else -> emptyList()
        }.mapNotNull { runCatching { parseRegex(it) }.getOrNull() }
    }.getOrDefault(emptyList())

    /**
     * 酒馆预设（Chat Completion preset）把脚本挂在 extensions.regex_scripts 下。
     * 直接导入整份预设时用这个抽取，避免用户再单独导一次正则文件。
     */
    fun parseFromPreset(json: String): List<AssistantRegex> = runCatching {
        val obj = Json.parseToJsonElement(json) as? JsonObject ?: return@runCatching emptyList()
        val scripts = (obj["extensions"] as? JsonObject)?.get("regex_scripts") as? JsonArray
            ?: return@runCatching emptyList()
        parse(scripts.toString())
    }.getOrDefault(emptyList())

    private fun parseRegex(obj: JsonObject): AssistantRegex? {
        val name = obj["scriptName"]?.jsonPrimitive?.contentOrNull ?: return null
        val findRegex = obj["findRegex"]?.jsonPrimitive?.contentOrNull ?: return null
        if (findRegex.isBlank()) return null
        val placement = obj["placement"]?.let {
            (it as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { p -> (p as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull }
                .orEmpty()
        }.orEmpty()
        // 官方未勾选任何位置时不生效；导入时保守地映射为两者，用户可在 UI 里再收窄
        val scopes = buildSet {
            if (placement.isEmpty() || 1 in placement) add(AssistantAffectScope.USER)
            if (placement.isEmpty() || 2 in placement) add(AssistantAffectScope.ASSISTANT)
        }
        // 官方语义三态：(F,F)=两层都生效（最常见）；(T,F)=仅显示；(F,T)=仅提示词。
        // promptOnly 必须单独记录：前端卡常在显示脚本之前放一条 promptOnly 的
        // 「对 AI 隐藏状态栏」，若也作用到显示层，占位标签会被提前清掉，卡片不渲染。
        // 两者都勾选 = 两层都生效，与都不勾选等价；不能映射成 promptOnly，否则显示层被跳过。
        val markdownOnly = obj["markdownOnly"]?.jsonPrimitive?.booleanOrNull ?: false
        val rawPromptOnly = obj["promptOnly"]?.jsonPrimitive?.booleanOrNull ?: false
        val visualOnly = markdownOnly && !rawPromptOnly
        val promptOnly = rawPromptOnly && !markdownOnly
        // 酒馆深度过滤：原值照搬，null = 不限制（maxDepth = 0 是「只作用于最新一条」，不能当成未设置）
        val minDepth = obj["minDepth"]?.jsonPrimitive?.intOrNull
        val maxDepth = obj["maxDepth"]?.jsonPrimitive?.intOrNull
        return AssistantRegex(
            id = Uuid.random(),
            name = name,
            enabled = !(obj["disabled"]?.jsonPrimitive?.booleanOrNull ?: false),
            findRegex = findRegex,
            replaceString = obj["replaceString"]?.jsonPrimitive?.contentOrNull ?: "",
            affectingScope = scopes,
            visualOnly = visualOnly,
            promptOnly = promptOnly,
            minDepth = minDepth,
            maxDepth = maxDepth,
        )
    }
}

/**
 * 酒馆快速回复（Quick Replies）导入：
 * - v2 格式：{"version":2,"name":"...","qrList":[{"label":"...","message":"...",...}]}
 * - v1 格式：{"name":"...","quickReplies":[...]} 或直接 [{"label":"...","message":"..."}]
 * 占位符参数（{{arg:1}} 等）由宏引擎在发送时处理，导入不展开。
 */
object SillyTavernQuickRepliesImporter {
    fun parse(json: String): List<QuickMessage> = runCatching {
        val element = Json.parseToJsonElement(json)
        val setName = (element as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
        val qrArray: JsonArray = when (element) {
            is JsonArray -> element
            is JsonObject -> (element["qrList"] ?: element["quickReplies"]) as? JsonArray ?: return emptyList()
            else -> return emptyList()
        }
        qrArray.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val message = obj["message"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (message.isBlank()) return@mapNotNull null
            QuickMessage(
                title = obj["label"]?.jsonPrimitive?.contentOrNull
                    ?: obj["title"]?.jsonPrimitive?.contentOrNull
                    ?: "",
                content = message,
                setName = setName,
                hidden = obj["isHidden"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }
    }.getOrDefault(emptyList())
}

@Serializable
private data class SillyTavernEntry(
    val key: List<String> = emptyList(),
    val keysecondary: List<String> = emptyList(),
    val content: String = "",
    val comment: String? = null,
    val constant: Boolean = false,
    val position: Int = 0,
    val order: Int = 100,
    val disable: Boolean = false,
    val depth: Int = 4,
    val scanDepth: Int? = null,
    val caseSensitive: Boolean? = null,
    val selective: Boolean = false,
    val selectiveLogic: Int = 0,
    val probability: Int? = 100,
    val useProbability: Boolean? = true,
    val group: String? = null,
    val groupWeight: Int? = 100,
    val groupOverride: Boolean? = null,
    val vectorized: Boolean? = null,
    val role: JsonElement? = null,
    val sticky: Int? = null,
    val cooldown: Int? = null,
    val delay: Int? = null,
    val excludeRecursion: Boolean? = null,
    val preventRecursion: Boolean? = null,
    val delayUntilRecursion: JsonElement? = null,
    val matchWholeWords: Boolean? = null,
    val extensions: JsonElement? = null,
)
