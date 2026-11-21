package me.rerere.rikkahub.data.datastore.migration

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.JsonInstant

class PreferenceStoreV6Migration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val version = currentData[SettingsStore.VERSION]
        return version == null || version < 6
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()

        prefs[SettingsStore.DISPLAY_SETTING]?.let { json ->
            prefs[SettingsStore.DISPLAY_SETTING] = migrateDisplaySettingQuoteHighlight(json)
        }

        prefs[SettingsStore.VERSION] = 6
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() {}
}

/**
 * 对话引号着色（enableQuoteHighlight）改为默认开启。
 * 旧版本保存过 DisplaySetting 时会把旧默认值 false 写进 JSON，覆盖升级后导致该开关仍为关闭。
 * 仅在尚未出现新字段 enableParenthesisHighlight 的旧配置里将其迁为 true，
 * 避免覆盖已经在新版本中主动关闭过该开关的用户选择。
 */
internal fun migrateDisplaySettingQuoteHighlight(displaySettingJson: String): String {
    return runCatching {
        val obj = JsonInstant.parseToJsonElement(displaySettingJson) as? JsonObject
            ?: return@runCatching displaySettingJson
        if (NEW_PARENTHESIS_KEY in obj) return@runCatching displaySettingJson

        JsonInstant.encodeToString(
            JsonObject(
                obj.toMutableMap().apply {
                    put(QUOTE_HIGHLIGHT_KEY, JsonPrimitive(true))
                }
            )
        )
    }.getOrDefault(displaySettingJson)
}

private const val QUOTE_HIGHLIGHT_KEY = "enableQuoteHighlight"
private const val NEW_PARENTHESIS_KEY = "enableParenthesisHighlight"
