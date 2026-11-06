package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.model.AssistantRegex
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.LorebookKind
import me.rerere.rikkahub.data.model.RegexGroup
import me.rerere.rikkahub.data.model.RegexGroupSource
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * 全局正则只应用「仍存在且启用」的预设分组；已删除/禁用预设的分组不再生效。
 */
class GlobalRegexesTest {
    private val presetId = Uuid.random()
    private val regexFromGroup = AssistantRegex(id = Uuid.random(), name = "预设正则")
    private val legacyRegex = AssistantRegex(id = Uuid.random(), name = "旧版正则")
    private val customRegex = AssistantRegex(id = Uuid.random(), name = "自定义正则")

    private fun presetGroup(presetId: Uuid?, regex: AssistantRegex) = RegexGroup(
        name = "预设",
        source = RegexGroupSource.PRESET,
        regexes = listOf(regex),
        presetId = presetId,
    )

    private fun preset(id: Uuid, enabled: Boolean) = Lorebook(
        id = id,
        name = "预设",
        enabled = enabled,
        kind = LorebookKind.PRESET,
    )

    @Test
    fun `preset group is included when preset exists and is enabled`() {
        val settings = Settings(
            lorebooks = listOf(preset(presetId, enabled = true)),
            regexGroups = listOf(presetGroup(presetId, regexFromGroup)),
        )

        assertEquals(listOf(regexFromGroup), settings.globalRegexes)
    }

    @Test
    fun `preset group is excluded when preset was deleted`() {
        val settings = Settings(
            lorebooks = emptyList(),
            regexGroups = listOf(presetGroup(presetId, regexFromGroup)),
        )

        assertEquals(emptyList<AssistantRegex>(), settings.globalRegexes)
    }

    @Test
    fun `preset group is excluded when preset is disabled`() {
        val settings = Settings(
            lorebooks = listOf(preset(presetId, enabled = false)),
            regexGroups = listOf(presetGroup(presetId, regexFromGroup)),
        )

        assertEquals(emptyList<AssistantRegex>(), settings.globalRegexes)
    }

    @Test
    fun `legacy preset group without presetId stays active`() {
        val settings = Settings(
            regexGroups = listOf(presetGroup(null, legacyRegex)),
        )

        assertEquals(listOf(legacyRegex), settings.globalRegexes)
    }

    @Test
    fun `custom group is always active regardless of presets`() {
        val settings = Settings(
            lorebooks = emptyList(),
            regexGroups = listOf(
                RegexGroup(name = "自定义", source = RegexGroupSource.CUSTOM, regexes = listOf(customRegex)),
                presetGroup(presetId, regexFromGroup),
            ),
        )

        assertEquals(listOf(customRegex), settings.globalRegexes)
    }
}
