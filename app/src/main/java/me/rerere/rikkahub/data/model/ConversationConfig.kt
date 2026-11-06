package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import kotlin.uuid.Uuid

/**
 * 会话开始后固定在会话上的配置。
 *
 * 新会话没有这份配置，聊天页里的切换直接改助手；会话落库时从助手拍一份快照，
 * 之后的切换只影响当前会话，别处的改动不会打乱它的上下文和缓存。
 * 模式注入和 Lorebook 同样随会话固定，沿用 [Conversation.modeInjectionIds] 与 [Conversation.lorebookIds]。
 */
@Serializable
data class GenerationLimits(
    val contextTokens: Int? = null,
    val maxReplyTokens: Int? = null,
)

@Serializable
data class ConversationConfig(
    val generationLimits: GenerationLimits? = null,
    val chatModelId: Uuid? = null,
    val reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    val enableWebSearch: Boolean = false,
    val builtInSearch: Boolean = false, // 模型内置搜索，覆盖模型自身的开关
    val mcpServers: Set<Uuid> = emptySet(),
    val workspaceId: Uuid? = null,
    val enabledSkills: Set<String> = emptySet(),
    /** Group chat this conversation belongs to. Null for a single-character chat. */
    val groupId: Uuid? = null,
    /** Who speaks next when the group strategy is manual, and the last choice otherwise. */
    val groupSpeakerId: Uuid? = null,
    /** Quick replies pinned to this chat, in addition to the character's set. */
    val quickReplyIds: Set<Uuid> = emptySet(),
)

// 内置搜索是模型的开关，不在助手上，由调用方另行填入
private fun Assistant.toConversationConfig() = ConversationConfig(
    generationLimits = GenerationLimits(contextTokenLimit, maxTokens),
    chatModelId = chatModelId,
    reasoningLevel = reasoningLevel,
    enableWebSearch = enableWebSearch,
    mcpServers = mcpServers,
    workspaceId = workspaceId,
    enabledSkills = enabledSkills,
)

private fun Assistant.withConversationConfig(config: ConversationConfig) = copy(
    contextTokenLimit = if (config.generationLimits != null) config.generationLimits.contextTokens else contextTokenLimit,
    maxTokens = if (config.generationLimits != null) config.generationLimits.maxReplyTokens else maxTokens,
    chatModelId = config.chatModelId,
    reasoningLevel = config.reasoningLevel,
    enableWebSearch = config.enableWebSearch,
    mcpServers = config.mcpServers,
    workspaceId = config.workspaceId,
    enabledSkills = config.enabledSkills,
)

/** 预设（[LorebookKind.PRESET]）的 id，预设始终跟随助手绑定；不在 [Settings.lorebooks] 里的 id 不算预设。 */
private fun Settings.presetIds(): Set<Uuid> =
    lorebooks.filter { it.kind == LorebookKind.PRESET }.mapTo(mutableSetOf()) { it.id }

/** 会话视角下的助手：会话开始后，会话持有的字段覆盖助手自身的值。 */
fun Assistant.withConversation(conversation: Conversation): Assistant {
    val config = conversation.config ?: return this
    return withConversationConfig(config).copy(
        modeInjectionIds = conversation.modeInjectionIds,
        lorebookIds = conversation.lorebookIds,
    )
}

/** 聊天页改动助手后留在会话上的部分，[updated] 是会话视角下改动后的助手。 */
fun Conversation.withAssistantUpdate(updated: Assistant, settings: Settings): Conversation {
    val previous = settings.getAssistantOf(this)
    val presets = settings.presetIds()
    val config = config
    val conversation = if (config == null) {
        this
    } else {
        copy(
            config = updated.toConversationConfig().copy(
                builtInSearch = if (updated.chatModelId == config.chatModelId) {
                    config.builtInSearch
                } else {
                    // 内置搜索是模型自身的能力，换模型后跟随新模型的开关
                    settings.findModelById(updated.chatModelId)?.tools?.contains(BuiltInTools.Search) == true
                },
                groupId = config.groupId,
                groupSpeakerId = config.groupSpeakerId,
                quickReplyIds = config.quickReplyIds,
            ),
            modeInjectionIds = updated.modeInjectionIds,
            // 预设不固定在会话上，继续读取助手自身的绑定
            lorebookIds = updated.lorebookIds.filterTo(mutableSetOf()) { it !in presets },
        )
    }
    // 工作目录属于原来的工作区，换工作区后失效
    return if (updated.workspaceId != previous.workspaceId) conversation.copy(workspaceCwd = null) else conversation
}

/**
 * 聊天页改动助手后写回助手设置的部分：会话持有的字段保持 [stored] 的原值。
 * 预设不随会话固定，会话里勾选的预设写回助手；世界书仍只改当前会话，这里保持 [stored] 的原值。
 */
fun Assistant.withoutConversationFields(conversation: Conversation, stored: Assistant, settings: Settings): Assistant =
    if (conversation.config == null) {
        this
    } else {
        val presets = settings.presetIds()
        withConversationConfig(stored.toConversationConfig()).copy(
            modeInjectionIds = stored.modeInjectionIds,
            lorebookIds = stored.lorebookIds.filterTo(mutableSetOf()) { it !in presets } +
                lorebookIds.filterTo(mutableSetOf()) { it in presets },
        )
    }

/** 会话所属助手自身的设置，不含会话上固定的配置；助手被删除后退回当前助手。 */
fun Settings.getStoredAssistantOf(conversation: Conversation): Assistant =
    getAssistantById(conversation.assistantId) ?: getCurrentAssistant()

/** 会话所属的助手（会话视角）。 */
fun Settings.getAssistantOf(conversation: Conversation): Assistant {
    val assistant = getStoredAssistantOf(conversation).withConversation(conversation)
    if (conversation.config == null) return assistant
    // 助手自身的失效引用在读取设置时已清理，会话上固定的这份在这里滤掉
    // 预设不固定在会话上：取助手自身的预设，会话上残留的预设忽略
    val presets = presetIds()
    val worldBooks = assistant.lorebookIds
        .filterTo(mutableSetOf()) { id -> id !in presets && lorebooks.any { it.id == id } }
    val storedPresets = getStoredAssistantOf(conversation).lorebookIds.filterTo(mutableSetOf()) { it in presets }
    return assistant.copy(
        mcpServers = assistant.mcpServers.filterTo(mutableSetOf()) { id -> mcpServers.any { it.id == id } },
        modeInjectionIds = assistant.modeInjectionIds
            .filterTo(mutableSetOf()) { id -> modeInjections.any { it.id == id } },
        lorebookIds = worldBooks + storedPresets,
    )
}

/** 会话使用的聊天模型；会话开始后内置搜索以会话上固定的开关为准。 */
fun Settings.getChatModelOf(conversation: Conversation): Model? {
    val assistantModel = findModelById(getStoredAssistantOf(conversation).chatModelId ?: chatModelId)
    val config = conversation.config ?: return assistantModel
    // 固定的模型被删除后退回助手当前的模型，内置搜索也跟随该模型自身的开关
    val model = findModelById(config.chatModelId) ?: return assistantModel
    return model.copy(
        tools = if (config.builtInSearch) model.tools + BuiltInTools.Search else model.tools - BuiltInTools.Search
    )
}

/**
 * 把助手当前的配置固定到会话上。已经固定过的会话原样返回，
 * 只有固定的模型被删除时才改为固定助手当前的模型，之后不再跟着助手变。
 */
fun Conversation.bindConfig(settings: Settings): Conversation {
    val model = settings.getChatModelOf(this)
    val builtInSearch = model != null && BuiltInTools.Search in model.tools
    val config = config
    if (config != null) {
        if (model == null || model.id == config.chatModelId) return this
        return copy(config = config.copy(chatModelId = model.id, builtInSearch = builtInSearch))
    }
    val assistant = settings.getAssistantOf(this)
    val presets = settings.presetIds()
    return copy(
        config = assistant.toConversationConfig().copy(chatModelId = model?.id, builtInSearch = builtInSearch),
        // 旧版本允许会话单独绑定注入，这类会话保留自己的绑定
        modeInjectionIds = modeInjectionIds.ifEmpty { assistant.modeInjectionIds },
        // 世界书固定在会话上，预设不固定，始终跟随助手
        lorebookIds = lorebookIds.ifEmpty { assistant.lorebookIds }.filterTo(mutableSetOf()) { it !in presets },
    )
}
