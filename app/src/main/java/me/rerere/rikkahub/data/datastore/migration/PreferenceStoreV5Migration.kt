package me.rerere.rikkahub.data.datastore.migration

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import me.rerere.rikkahub.data.ai.cards.DEFAULT_HTML_CARD_ALLOWLIST
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.JsonInstant

class PreferenceStoreV5Migration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val version = currentData[SettingsStore.VERSION]
        return version == null || version < 5
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()

        prefs[SettingsStore.HTML_CARD_ALLOWLIST] = migrateHtmlCardAllowlist(prefs[SettingsStore.HTML_CARD_ALLOWLIST])

        prefs[SettingsStore.VERSION] = 5
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() {}
}

/**
 * 旧版本的 HTML 卡片域名默认为空（拦截所有网络请求），设置保存时会把空列表写进去，
 * 和用户主动清空分不出来。这里把空的或缺失的一律换成默认的常用 CDN，用户自己填过的保持不变。
 */
internal fun migrateHtmlCardAllowlist(json: String?): String {
    val hosts = json?.let { runCatching { JsonInstant.decodeFromString<List<String>>(it) }.getOrNull() }
    return if (hosts.isNullOrEmpty()) JsonInstant.encodeToString(DEFAULT_HTML_CARD_ALLOWLIST) else json
}
