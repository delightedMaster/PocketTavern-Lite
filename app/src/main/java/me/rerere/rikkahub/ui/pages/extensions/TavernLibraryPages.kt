package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.LorebookKind
import me.rerere.rikkahub.data.model.RegexGroupSource
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel

// 设置页直达的三个独立页面：世界书、正则、预设。
// 复用 PromptPage 中的 LorebookTab / RegexScriptsTab，只负责过滤与回写。

@Composable
fun LorebooksPage(vm: PromptVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    TavernLibraryScaffold(titleRes = R.string.setting_page_lorebooks) {
        LorebookTab(
            kind = LorebookKind.WORLD_BOOK,
            lorebooks = settings.lorebooks.filter { it.kind == LorebookKind.WORLD_BOOK },
            onUpdate = { updated -> vm.updateLorebooksOfKind(LorebookKind.WORLD_BOOK, updated) },
            onImport = vm::importLorebook,
        )
    }
}

@Composable
fun RegexScriptsPage(vm: PromptVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    TavernLibraryScaffold(titleRes = R.string.setting_page_regex) {
        RegexScriptsTab(
            groups = settings.regexGroups,
            onUpdate = { groups ->
                vm.updateSettings { latest -> latest.copy(regexGroups = groups) }
            },
        )
    }
}

@Composable
fun PresetsPage(vm: PromptVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    TavernLibraryScaffold(titleRes = R.string.setting_page_presets) {
        LorebookTab(
            kind = LorebookKind.PRESET,
            lorebooks = settings.lorebooks.filter { it.kind == LorebookKind.PRESET },
            onUpdate = { updated -> vm.updatePresets(updated) },
            onImport = vm::importLorebook,
        )
    }
}

// 回写时只替换同类条目，另一类（世界书 / 预设）保持不变
private fun PromptVM.updateLorebooksOfKind(kind: LorebookKind, updated: List<Lorebook>) {
    updateSettings { latest ->
        latest.copy(lorebooks = mergeLorebooksOfKind(latest.lorebooks, kind, updated))
    }
}

// 预设被删除时，同一次设置写入里一并移除它附带的正则分组，避免孤儿分组残留
private fun PromptVM.updatePresets(updated: List<Lorebook>) {
    updateSettings { latest ->
        val keptIds = updated.map { it.id }.toSet()
        val removedIds = latest.lorebooks
            .filter { it.kind == LorebookKind.PRESET && it.id !in keptIds }
            .map { it.id }
            .toSet()
        latest.copy(
            lorebooks = mergeLorebooksOfKind(latest.lorebooks, LorebookKind.PRESET, updated),
            regexGroups = latest.regexGroups.filterNot { group ->
                val presetId = group.presetId
                group.source == RegexGroupSource.PRESET && presetId != null && presetId in removedIds
            },
        )
    }
}

/**
 * 把 [updated]（仅含 [kind] 类条目，已按页面顺序排列）合并回全局列表 [all]。
 * 另一类条目保持原位；同类条目按位置依次填入原有槽位，
 * 槽位用完则视为删除，[updated] 多出的条目追加到末尾。
 * 全局顺序稳定，避免影响 LayeredPrompt 的提示词缓存前缀。
 */
internal fun mergeLorebooksOfKind(all: List<Lorebook>, kind: LorebookKind, updated: List<Lorebook>): List<Lorebook> {
    val result = mutableListOf<Lorebook>()
    var next = 0
    for (item in all) {
        if (item.kind != kind) {
            result += item
        } else if (next < updated.size) {
            result += updated[next]
            next++
        }
    }
    while (next < updated.size) {
        result += updated[next]
        next++
    }
    return result
}

@Composable
private fun TavernLibraryScaffold(
    titleRes: Int,
    content: @Composable () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                navigationIcon = { BackButton() },
                title = { Text(stringResource(titleRes)) },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            content()
        }
    }
}
