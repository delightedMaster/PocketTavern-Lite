package me.rerere.rikkahub.data.ai.transformers

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.uuid.Uuid

/**
 * 开场白里的 {{setvar}} 必须按新会话 id 落在本对话变量上，不能写进全局，也不能被另一个会话读到。
 * {{setglobalvar}} 仍然全局可见。
 */
class GreetingMacroVariableTest {
    @get:Rule
    val temp = TemporaryFolder()

    private var scope: CoroutineScope? = null

    @After
    fun tearDown() {
        scope?.cancel()
    }

    @Test
    fun `greeting setvar stays on that conversation`() = runBlocking {
        val store = openStore()
        val settings = store.awaitLoaded()
        val assistant = Assistant()
        val conversationA = Uuid.random()
        val conversationB = Uuid.random()

        val renderedA = PlaceholderTransformer.applyGreetingMacros(
            context = null,
            settingsStore = store,
            settings = settings,
            assistant = assistant,
            text = "{{setvar::hp::1}}{{addvar::hp::2}}{{incvar::hp}}{{getvar::hp}}{{setglobalvar::flag::9}}",
            conversationId = conversationA,
        )
        // setvar/addvar/setglobalvar 不输出文本；incvar 与随后的 getvar 都是 4
        assertEquals("44", renderedA)

        val afterA = store.settingsFlow.value
        assertEquals("4", afterA.macroChatVariables[conversationA.toString()]?.get("hp"))
        assertNull(afterA.macroChatVariables[conversationB.toString()]?.get("hp"))
        assertNull(afterA.macroGlobalVariables["hp"])
        assertEquals("9", afterA.macroGlobalVariables["flag"])

        val renderedB = PlaceholderTransformer.applyGreetingMacros(
            context = null,
            settingsStore = store,
            settings = store.settingsFlow.value,
            assistant = assistant,
            text = "{{getvar::hp}}|{{getglobalvar::flag}}{{setvar::hp::99}}",
            conversationId = conversationB,
        )
        assertEquals("|9", renderedB)

        val afterB = store.settingsFlow.value
        assertEquals("4", afterB.macroChatVariables[conversationA.toString()]?.get("hp"))
        assertEquals("99", afterB.macroChatVariables[conversationB.toString()]?.get("hp"))
        assertNull(afterB.macroGlobalVariables["hp"])
        assertEquals("9", afterB.macroGlobalVariables["flag"])
    }

    private fun openStore(): SettingsStore {
        val opened = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = opened
        val disk = PreferenceDataStoreFactory.create(scope = opened) {
            File(temp.root, "settings.preferences_pb")
        }
        return SettingsStore(disk, opened)
    }
}
