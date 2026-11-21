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

    @Test
    fun `disabled regex group is excluded for both custom and preset sources`() {
        val settings = Settings(
            lorebooks = listOf(preset(presetId, enabled = true)),
            regexGroups = listOf(
                RegexGroup(name = "自定义", enabled = false, source = RegexGroupSource.CUSTOM, regexes = listOf(customRegex)),
                presetGroup(presetId, regexFromGroup).copy(enabled = false),
            ),
        )

        assertEquals(emptyList<AssistantRegex>(), settings.globalRegexes)
        val assistant = me.rerere.rikkahub.data.model.Assistant(lorebookIds = setOf(presetId))
        assertEquals(emptyList<AssistantRegex>(), settings.globalRegexesFor(assistant))
    }

    @Test
    fun `assistant only activates regex group matching its active preset`() {
        val presetA = Uuid.random()
        val presetB = Uuid.random()
        val regexA = AssistantRegex(id = Uuid.random(), name = "预设A正则")
        val regexB = AssistantRegex(id = Uuid.random(), name = "预设B正则")
        val cardRegex = AssistantRegex(id = Uuid.random(), name = "角色卡正则")
        val settings = Settings(
            lorebooks = listOf(
                preset(presetA, enabled = true),
                preset(presetB, enabled = true),
            ),
            regexGroups = listOf(
                RegexGroup(name = "自定义", source = RegexGroupSource.CUSTOM, regexes = listOf(customRegex)),
                presetGroup(presetA, regexA),
                presetGroup(presetB, regexB),
            ),
        )

        val assistantUsingA = me.rerere.rikkahub.data.model.Assistant(
            regexes = listOf(cardRegex),
            lorebookIds = setOf(presetA),
        )
        assertEquals(listOf(cardRegex, customRegex, regexA), settings.allRegexesOf(assistantUsingA))

        val assistantUsingB = assistantUsingA.copy(lorebookIds = setOf(presetB))
        assertEquals(listOf(cardRegex, customRegex, regexB), settings.allRegexesOf(assistantUsingB))

        val assistantUsingNone = assistantUsingA.copy(lorebookIds = emptySet())
        assertEquals(listOf(cardRegex, customRegex), settings.allRegexesOf(assistantUsingNone))
    }

    @Test
    fun `bound preset disabled globally does not activate its regex group`() {
        val settings = Settings(
            lorebooks = listOf(preset(presetId, enabled = false)),
            regexGroups = listOf(presetGroup(presetId, regexFromGroup)),
        )
        val assistant = me.rerere.rikkahub.data.model.Assistant(lorebookIds = setOf(presetId))

        assertEquals(emptyList<AssistantRegex>(), settings.globalRegexesFor(assistant))
    }
}
