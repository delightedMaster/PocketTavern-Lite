package me.rerere.rikkahub.data.datastore

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.ProviderSetting
import me.rerere.asr.ASRProviderSetting
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.AssistantRegex
import me.rerere.rikkahub.data.model.AuthorNoteEntry
import me.rerere.rikkahub.data.model.AuthorNotePosition
import me.rerere.rikkahub.data.model.GroupChat
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.Persona
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.data.model.RegexGroup
import me.rerere.rikkahub.data.model.RegexGroupSource
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.ui.theme.CustomTheme
import me.rerere.search.SearchCommonOptions
import me.rerere.search.SearchServiceOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.uuid.Uuid

class SettingsFieldsTest {
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `every settings property has exactly one field`() {
        val properties = Settings.serializer().descriptor.elementNames.sorted()

        assertEquals(properties, SETTINGS_FIELDS.map { it.property.name }.sorted())
    }

    @Test
    fun `settings survive a round trip through preferences`() {
        // 样本里和默认值相同的字段测不出读写是否对得上
        val defaults = emptyPreferences().toSettings()
        assertEquals(
            emptyList<String>(),
            SETTINGS_FIELDS.filterNot { it.changed(defaults, SAMPLE) }.map { it.property.name },
        )

        val preferences = mutablePreferencesOf().apply { putSettings(SAMPLE) }

        assertEquals(SAMPLE, preferences.toSettings())
    }

    // 这里的 key 名和取值就是用户盘上的格式，改动它们等于丢掉已有的设置
    @Test
    fun `stored format stays compatible with existing data`() {
        val stored = mutablePreferencesOf().apply { putSettings(SAMPLE) }.asMap().mapKeys { it.key.name }

        assertEquals(STORED_KEYS, stored.keys)
        assertEquals(false, stored["dynamic_color"])
        assertEquals(9090, stored["web_server_port"])
        assertEquals(1.5f, stored["default_tts_playback_speed"])
        assertEquals("title", stored["title_prompt"])
        assertEquals("00000000-0000-0000-0000-000000000001", stored["chat_model"])
        assertEquals("LOW", stored["fast_model_reasoning_level"])
        assertEquals("""[{"id":"00000000-0000-0000-0000-000000000007","name":"tag"}]""", stored["assistant_tags"])
        assertEquals("""{"resultSize":3}""", stored["search_common"])
    }

    @Test
    fun `missing keys read as first launch values`() {
        val settings = emptyPreferences().toSettings()

        assertEquals(DEFAULT_AUTO_MODEL_ID, settings.chatModelId)
        assertEquals(DEFAULT_AUTO_MODEL_ID, settings.fastModelId)
        assertEquals(DEFAULT_AUTO_MODEL_ID, settings.translateModeId)
        assertEquals(DEFAULT_MODE_INJECTIONS, settings.modeInjections)
        assertEquals(DEFAULT_ASSISTANT_ID, settings.assistantId)
        assertEquals(DEFAULT_SYSTEM_TTS_ID, settings.selectedTTSProviderId)
        assertEquals(null, settings.selectedASRProviderId)
        assertEquals(true, settings.dynamicColor)
        assertEquals(8080, settings.webServerPort)
    }

    @Test
    fun `unknown reasoning level reads as auto`() {
        val preferences = mutablePreferencesOf(SettingsStore.FAST_MODEL_REASONING_LEVEL to "REMOVED_LEVEL")

        assertEquals(ReasoningLevel.AUTO, preferences.toSettings().fastModelReasoningLevel)
    }

    @Test
    fun `clearing the asr provider removes its key`() {
        val preferences = mutablePreferencesOf().apply { putSettings(SAMPLE) }

        preferences.putSettings(SAMPLE.copy(selectedASRProviderId = null), persisted = SAMPLE)

        assertFalse(SettingsStore.SELECTED_ASR_PROVIDER in preferences)
        assertEquals(null, preferences.toSettings().selectedASRProviderId)
    }

    @Test
    fun `only changed fields are written`() {
        val preferences = mutablePreferencesOf().apply { putSettings(SAMPLE) }
        // 盘上的值故意和已落盘的那份不一致：没变的字段如果也被重写，它就会变回去
        preferences[SettingsStore.TITLE_PROMPT] = "on disk"

        preferences.putSettings(SAMPLE.copy(themeId = "changed"), persisted = SAMPLE)

        assertEquals("changed", preferences[SettingsStore.THEME_ID])
        assertEquals("on disk", preferences[SettingsStore.TITLE_PROMPT])
    }
}

private fun id(n: Int) = Uuid.parse("00000000-0000-0000-0000-%012d".format(n))

// 每个字段都取了和默认值不同的值
private val SAMPLE = Settings(
    dynamicColor = false,
    themeId = "custom",
    customThemes = listOf(CustomTheme(id = "theme", name = "Theme")),
    developerMode = true,
    displaySetting = DisplaySetting(userNickname = "me"),
    networkSetting = NetworkSetting(userAgent = "agent"),
    favoriteModels = listOf(id(30)),
    chatModelId = id(1),
    fastModelId = id(2),
    fastModelReasoningLevel = ReasoningLevel.LOW,
    imageGenerationModelId = id(3),
    enableAutoTitle = false,
    titlePrompt = "title",
    translateModeId = id(4),
    translatePrompt = "translate",
    translateThinkingBudget = 1024,
    translateTargetLanguage = "ja",
    enableSuggestion = false,
    suggestionPrompt = "suggestion",
    ocrModelId = id(5),
    ocrPrompt = "ocr",
    assistantId = id(20),
    providers = listOf(ProviderSetting.OpenAI(id = id(31), name = "Provider")),
    assistants = listOf(Assistant(id = id(20), name = "Assistant")),
    assistantTags = listOf(Tag(id = id(7), name = "tag")),
    searchServices = listOf(
        SearchServiceOptions.BingLocalOptions(id = id(21)),
        SearchServiceOptions.BingLocalOptions(id = id(22)),
    ),
    searchCommonOptions = SearchCommonOptions(resultSize = 3),
    searchServiceSelected = 1,
    mcpServers = listOf(McpServerConfig.SseTransportServer(id = id(8), url = "https://example.com/sse")),
    webDavConfig = WebDavConfig(url = "https://dav.example.com"),
    s3Config = S3Config(bucket = "backup"),
    uploadS3Config = S3Config(bucket = "upload"),
    mediaGenerationProviders = listOf(MediaGenerationProviderSetting.OpenAI(id = id(9))),
    ttsProviders = DEFAULT_TTS_PROVIDERS.take(1),
    selectedTTSProviderId = id(10),
    defaultTTSPlaybackSpeed = 1.5f,
    asrProviders = listOf(ASRProviderSetting.OpenAIRealtime(id = id(11))),
    selectedASRProviderId = id(11),
    modeInjections = listOf(PromptInjection.ModeInjection(id = id(14), name = "mode")),
    lorebooks = listOf(Lorebook(id = id(12), name = "lorebook")),
    regexGroups = listOf(
        RegexGroup(
            id = id(32),
            name = "scripts",
            source = RegexGroupSource.PRESET,
            regexes = listOf(AssistantRegex(id = id(33), affectingScope = setOf(AssistantAffectScope.ASSISTANT))),
        ),
    ),
    quickMessages = listOf(QuickMessage(id = id(13), title = "hi")),
    worldInfoBudget = 200,
    worldInfoBudgetCap = 512,
    worldInfoMinActivations = 2,
    worldInfoMinActivationsDepthMax = 8,
    worldInfoRecursive = true,
    worldInfoMaxRecursionSteps = 3,
    worldInfoDepth = 4,
    worldInfoCharacterStrategy = 2,
    worldInfoOverflowAlert = true,
    worldInfoUseGroupScoring = true,
    personas = listOf(Persona(id = id(34), name = "persona", description = "desc")),
    activePersonaId = id(34),
    authorNote = "note",
    authorNoteEnabled = true,
    authorNotePosition = AuthorNotePosition.IN_PROMPT,
    authorNoteDepth = 2,
    authorNoteRole = MessageRole.USER,
    authorNoteInterval = 3,
    summaryEnabled = false,
    summaryTriggerFloors = 12,
    summaryKeepFloors = 4,
    summaryConsolidationTokens = 100,
    summaryDeleteArchived = true,
    summaryReviewBeforeCommit = true,
    summaryUseChatModel = false,
    summaryModelId = id(36),
    summaryPrompt = "custom summary",
    authorNoteEntries = listOf(AuthorNoteEntry(id = "note", content = "body")),
    macroGlobalVariables = mapOf("hp" to "1"),
    macroChatVariables = mapOf("chat" to mapOf("mood" to "ok")),
    groupChats = listOf(GroupChat(id = id(35), name = "table")),
    webServerEnabled = true,
    webServerPort = 9090,
    webServerJwtEnabled = true,
    webServerAccessPassword = "secret",
    webServerLocalhostOnly = true,
    backupReminderConfig = BackupReminderConfig(enabled = true),
    sponsorAlertDismissedAt = 7,
)

private val STORED_KEYS = setOf(
    "dynamic_color",
    "theme_id",
    "custom_themes",
    "display_setting",
    "network_setting",
    "developer_mode",
    "favorite_models",
    "chat_model",
    "fast_model",
    "fast_model_reasoning_level",
    "translate_model",
    "enable_suggestion",
    "enable_auto_title",
    "image_generation_model",
    "title_prompt",
    "translation_prompt",
    "translate_thinking_budget",
    "translate_target_language",
    "suggestion_prompt",
    "ocr_model",
    "ocr_prompt",
    "providers",
    "select_assistant",
    "assistants",
    "assistant_tags",
    "search_services",
    "search_common",
    "search_selected",
    "mcp_servers",
    "webdav_config",
    "s3_config",
    "upload_s3_config",
    "media_generation_providers",
    "tts_providers",
    "selected_tts_provider",
    "default_tts_playback_speed",
    "asr_providers",
    "selected_asr_provider",
    "web_server_enabled",
    "web_server_port",
    "web_server_jwt_enabled",
    "web_server_access_password",
    "web_server_localhost_only",
    "mode_injections",
    "lorebooks",
    "regex_groups",
    "quick_messages",
    "world_info_budget",
    "world_info_budget_cap",
    "world_info_min_activations",
    "world_info_min_activations_depth_max",
    "world_info_recursive",
    "world_info_max_recursion_steps",
    "world_info_depth",
    "world_info_character_strategy",
    "world_info_overflow_alert",
    "world_info_use_group_scoring",
    "personas",
    "active_persona_id",
    "author_note",
    "author_note_enabled",
    "author_note_position",
    "author_note_depth",
    "author_note_role",
    "author_note_interval",
    "summary_enabled",
    "summary_trigger_floors",
    "summary_keep_floors",
    "summary_consolidation_tokens",
    "summary_delete_archived",
    "summary_review_before_commit",
    "summary_use_chat_model",
    "summary_model_id",
    "summary_prompt",
    "author_note_entries",
    "macro_global_variables",
    "macro_chat_variables",
    "group_chats",
    "backup_reminder_config",
    "sponsor_alert_dismissed_at",
)
