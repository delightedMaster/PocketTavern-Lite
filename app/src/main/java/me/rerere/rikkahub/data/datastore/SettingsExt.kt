package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantRegex
import me.rerere.rikkahub.data.model.AuthorNoteEntry
import me.rerere.rikkahub.data.model.LEGACY_AUTHOR_NOTE_ENTRY_ID
import kotlin.uuid.Uuid

/**
 * null 与空列表刻意区分：null 才回退旧字段；用户删光条目后保存 emptyList，
 * 旧 authorNote 不会被再次迁移出来。
 */
/** Card scripts run first, then global groups, matching SillyTavern's local-then-global order. */
fun Settings.allRegexesOf(assistant: Assistant?): List<AssistantRegex> =
    assistant?.regexes.orEmpty() + globalRegexes

fun Settings.authorNoteEntriesOrLegacy(): List<AuthorNoteEntry> {
    authorNoteEntries?.let { return it }
    if (authorNote.isBlank()) return emptyList()
    return listOf(
        AuthorNoteEntry(
            id = LEGACY_AUTHOR_NOTE_ENTRY_ID,
            name = "旧版自定义导演备注",
            content = authorNote,
            enabled = true,
            position = authorNotePosition,
            depth = authorNoteDepth,
            role = authorNoteRole,
            interval = authorNoteInterval,
        ),
    )
}

/** 所有提供商都还没有添加模型，视为尚未完成配置。 */
fun Settings.isNotConfigured() = providers.all { it.models.isEmpty() }

/**
 * 按 ID 查找模型。
 *
 * @param fallback [uuid] 为空或已找不到对应模型时改用的模型 ID
 */
fun Settings.findModelById(uuid: Uuid?, fallback: Uuid? = null): Model? {
    if (uuid == null && fallback == null) return null
    return uuid?.let { this.providers.findModelById(it) }
        ?: fallback?.let { this.providers.findModelById(it) }
}

/** 在所有提供商的模型里按 ID 查找。 */
fun List<ProviderSetting>.findModelById(uuid: Uuid): Model? {
    this.forEach { setting ->
        setting.models.forEach { model ->
            if (model.id == uuid) {
                return model
            }
        }
    }
    return null
}

/**
 * 当前助手的聊天模型，助手没有单独指定时用全局聊天模型。
 *
 * 已持久化的会话有自己的配置快照，应改用 [me.rerere.rikkahub.data.model.getChatModelOf]。
 */
fun Settings.getCurrentChatModel(): Model? {
    return findModelById(this.getCurrentAssistant().chatModelId ?: this.chatModelId)
}

/**
 * 当前选中的助手，选中的 ID 已不存在时回退到第一个助手。
 *
 * 已持久化的会话应改用 [me.rerere.rikkahub.data.model.getAssistantOf]。
 */
fun Settings.getCurrentAssistant(): Assistant {
    return this.assistants.find { it.id == assistantId } ?: this.assistants.first()
}

/** 按 ID 查找助手，不存在时返回 null，不做回退。 */
fun Settings.getAssistantById(id: Uuid): Assistant? {
    return this.assistants.find { it.id == id }
}

/** 该助手绑定的快捷消息，顺序跟随全局快捷消息列表。 */
fun Settings.getQuickMessagesOfAssistant(assistant: Assistant) =
    quickMessages.filter { it.id in assistant.quickMessageIds }

/**
 * 查找模型实际使用的提供商配置。
 *
 * 模型设置了 [Model.providerOverwrite] 时返回这份覆盖配置（不带模型列表），它不在 [providers] 里。
 *
 * @param checkOverwrite 为 false 时忽略覆盖配置，始终返回模型所属的提供商
 * @return 模型不属于 [providers] 里任何提供商时返回 null，即使它带有覆盖配置
 */
fun Model.findProvider(providers: List<ProviderSetting>, checkOverwrite: Boolean = true): ProviderSetting? {
    val provider = findModelProviderFromList(providers) ?: return null
    val providerOverwrite = this.providerOverwrite
    if (checkOverwrite && providerOverwrite != null) {
        return providerOverwrite.copyProvider(models = emptyList())
    }
    return provider
}

/** 模型所属的提供商，即模型列表里包含它的那个。 */
private fun Model.findModelProviderFromList(providers: List<ProviderSetting>): ProviderSetting? {
    providers.forEach { setting ->
        setting.models.forEach { model ->
            if (model.id == this.id) {
                return setting
            }
        }
    }
    return null
}


/** The chat UI currently requires at least one role; any template may be removed once another role exists. */
internal fun Settings.withoutAssistant(assistantId: Uuid): Settings {
    val remaining = assistants.filterNot { it.id == assistantId }
    if (remaining.isEmpty() || remaining.size == assistants.size) return this
    return copy(
        assistants = remaining,
        assistantId = if (this.assistantId == assistantId) remaining.first().id else this.assistantId,
    )
}
