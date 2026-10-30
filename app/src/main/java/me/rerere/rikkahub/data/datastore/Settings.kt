package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.ai.cards.DEFAULT_HTML_CARD_ALLOWLIST
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_SUGGESTION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TITLE_PROMPT
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantRegex
import me.rerere.rikkahub.data.model.AuthorNoteEntry
import me.rerere.rikkahub.data.model.AuthorNotePosition
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.GroupChat
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.Persona
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.data.model.RegexGroup
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.ui.theme.CustomTheme
import me.rerere.rikkahub.ui.theme.PresetThemes
import me.rerere.search.SearchCommonOptions
import me.rerere.search.SearchServiceOptions
import kotlin.uuid.Uuid

@Serializable
data class Settings(
    @Transient
    val init: Boolean = false,
    val dynamicColor: Boolean = true,
    val themeId: String = PresetThemes[0].id,
    val customThemes: List<CustomTheme> = emptyList(),
    val developerMode: Boolean = false,
    val displaySetting: DisplaySetting = DisplaySetting(),
    val networkSetting: NetworkSetting = NetworkSetting(),
    val favoriteModels: List<Uuid> = emptyList(),
    val chatModelId: Uuid = Uuid.random(),
    val fastModelId: Uuid = Uuid.random(),
    val fastModelReasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    val enableAutoTitle: Boolean = true,
    val titlePrompt: String = DEFAULT_TITLE_PROMPT,
    val enableSuggestion: Boolean = true,
    val suggestionPrompt: String = DEFAULT_SUGGESTION_PROMPT,
    val ocrModelId: Uuid = Uuid.random(),
    val ocrPrompt: String = "",
    val assistantId: Uuid = DEFAULT_ASSISTANT_ID,
    val providers: List<ProviderSetting> = DEFAULT_PROVIDERS,
    val assistants: List<Assistant> = DEFAULT_ASSISTANTS,
    val assistantTags: List<Tag> = emptyList(),
    val searchServices: List<SearchServiceOptions> = listOf(SearchServiceOptions.DEFAULT),
    val searchCommonOptions: SearchCommonOptions = SearchCommonOptions(),
    val searchServiceSelected: Int = 0,
    val mcpServers: List<McpServerConfig> = emptyList(),
    val webDavConfig: WebDavConfig = WebDavConfig(),
    val s3Config: S3Config = S3Config(),
    val modeInjections: List<PromptInjection.ModeInjection> = DEFAULT_MODE_INJECTIONS,
    val lorebooks: List<Lorebook> = emptyList(),
    val regexGroups: List<RegexGroup> = emptyList(),
    val quickMessages: List<QuickMessage> = emptyList(),
    // 世界书预算：100 = 不按百分比裁剪。低于 100 或设置绝对上限时才丢条目。
    val worldInfoBudget: Int = 100,
    val worldInfoBudgetCap: Int = 0,
    val worldInfoMinActivations: Int = 0,
    val worldInfoMinActivationsDepthMax: Int = 0,
    val worldInfoRecursive: Boolean = false,
    val worldInfoMaxRecursionSteps: Int = 0,
    val worldInfoDepth: Int = 2,
    val worldInfoCharacterStrategy: Int = 1,
    val worldInfoOverflowAlert: Boolean = false,
    val worldInfoUseGroupScoring: Boolean = false,
    val personas: List<Persona> = emptyList(),
    val activePersonaId: Uuid? = null,
    val authorNote: String = "",
    val authorNoteEnabled: Boolean = false,
    val authorNotePosition: AuthorNotePosition = AuthorNotePosition.IN_CHAT,
    val authorNoteDepth: Int = 4,
    val authorNoteRole: MessageRole = MessageRole.SYSTEM,
    val authorNoteInterval: Int = 1,
    /** Summarize once the live floor count reaches this. 0 disables the automatic pass. */
    val summaryEnabled: Boolean = true,
    val summaryTriggerFloors: Int = 40,
    val summaryKeepFloors: Int = 10,
    /** When the memory lorebook is larger than this, fold older blocks into a recap. */
    val summaryConsolidationTokens: Int = 2000,
    /** False archives the summarized floors. True deletes them. */
    val summaryDeleteArchived: Boolean = false,
    val summaryReviewBeforeCommit: Boolean = false,
    val summaryUseChatModel: Boolean = true,
    val summaryModelId: Uuid? = null,
    /** Empty uses the built-in prompt for the current language. */
    val summaryPrompt: String = "",
    /** Hosts an HTML card may fetch. Empty blocks every network request. */
    val htmlCardAllowlist: List<String> = DEFAULT_HTML_CARD_ALLOWLIST,
    // null = 尚未物化的新格式，继续兼容旧 authorNote
    val authorNoteEntries: List<AuthorNoteEntry>? = null,
    val macroGlobalVariables: Map<String, String> = emptyMap(),
    val macroChatVariables: Map<String, Map<String, String>> = emptyMap(),
    // 群聊成员卡按 memberIds 的固定顺序放进前缀。记忆世界书是会话自己的那一份。
    val groupChats: List<GroupChat> = emptyList(),
    val backupReminderConfig: BackupReminderConfig = BackupReminderConfig(),
) {
    val globalRegexes: List<AssistantRegex>
        get() = regexGroups.flatMap { it.regexes }

    companion object {
        // 构造一个用于初始化的settings, 但它不能用于保存，防止使用初始值存储
        fun dummy() = Settings(init = true)
    }
}

@Serializable
data class NetworkSetting(
    val userAgent: String = "",
    val proxyUrl: String = "",
    val proxyUsername: String = "",
    val proxyPassword: String = "",
    val enableAutoRetry: Boolean = true,
)

@Serializable
enum class ChatFontFamily {
    @SerialName("default")
    DEFAULT,
    @SerialName("serif")
    SERIF,
    @SerialName("monospace")
    MONOSPACE,

    @SerialName("custom")
    CUSTOM,
}

@Serializable
enum class BackgroundEffectType {
    @SerialName("blur")
    BLUR,

    @SerialName("glass")
    GLASS,
}

@Serializable
enum class ConversationSortOrder {
    @SerialName("update_time")
    UPDATE_TIME,

    @SerialName("create_time")
    CREATE_TIME,
}

@Serializable
data class DisplaySetting(
    val userAvatar: Avatar = Avatar.Dummy,
    val userNickname: String = "",
    val showUserAvatar: Boolean = true,
    val showAssistantBubble: Boolean = false,
    val bubbleOpacity: Float = 1.0f,
    val showModelIcon: Boolean = true,
    val showModelName: Boolean = true,
    val showDateTimeInMessage: Boolean = false,
    val showTokenUsage: Boolean = true,
    val showThinkingContent: Boolean = true,
    val autoCloseThinking: Boolean = true,
    val updateCheckEnabled: Boolean = true,
    val updateCheckDisabledUntilEpochMillis: Long = 0L,
    val showMessageJumper: Boolean = true,
    val messageJumperOnLeft: Boolean = false,
    val fontSizeRatio: Float = 1.0f,
    val enableMessageGenerationHapticEffect: Boolean = false,
    val skipCropImage: Boolean = true,
    val enableNotificationOnMessageGeneration: Boolean = false,
    val enableLiveUpdateNotification: Boolean = false,
    val codeBlockAutoWrap: Boolean = false,
    val codeBlockAutoCollapse: Boolean = false,
    val showLineNumbers: Boolean = false,
    val sendOnEnter: Boolean = false,
    val enableAutoScroll: Boolean = true,
    val enableLatexRendering: Boolean = true,
    val enableQuoteHighlight: Boolean = false,
    val enableBlurEffect: Boolean = false,
    val backgroundEffectType: BackgroundEffectType = BackgroundEffectType.BLUR,
    val chatFontFamily: ChatFontFamily = ChatFontFamily.DEFAULT,
    val chatCustomFontPath: String = "",
    val chatCustomFontName: String = "",
    val enableVolumeKeyScroll: Boolean = false,
    val volumeKeyScrollRatio: Float = 1.0f,
    val conversationSortOrder: ConversationSortOrder = ConversationSortOrder.UPDATE_TIME,
)

@Serializable
data class WebDavConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val path: String = "rikkahub_backups",
    val items: List<BackupItem> = listOf(
        BackupItem.DATABASE,
        BackupItem.FILES
    ),
) {
    @Serializable
    enum class BackupItem {
        DATABASE,
        FILES,
    }
}

@Serializable
data class BackupReminderConfig(
    val enabled: Boolean = false,
    val intervalDays: Int = 7,
    val lastBackupTime: Long = 0L,
)
