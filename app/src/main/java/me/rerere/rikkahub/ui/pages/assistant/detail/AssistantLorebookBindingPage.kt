package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Book01
import me.rerere.hugeicons.stroke.Layers01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.LorebookKind
import me.rerere.rikkahub.data.model.lorebooksOfKind
import me.rerere.rikkahub.data.model.toggleLorebookBinding
import me.rerere.rikkahub.ui.components.ai.ExtensionEmptyState
import me.rerere.rikkahub.ui.components.ai.LorebooksContent
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 助手绑定世界书 / 预设。内容本身在全局设置里管理（同名入口），这里只选择这个助手用哪些。
 * 从「扩展管理」里拆出来，与全局设置的「世界书」「预设」入口一一对应。
 */
@Composable
fun AssistantLorebookBindingPage(id: String, kind: LorebookKind) {
    val vm: AssistantDetailVM = koinViewModel(parameters = { parametersOf(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceBright,
    )
    val isPreset = kind == LorebookKind.PRESET
    val manageScreen: Screen = if (isPreset) Screen.Presets else Screen.Lorebooks
    val books = lorebooksOfKind(settings.lorebooks, kind)

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(if (isPreset) R.string.setting_page_presets else R.string.setting_page_lorebooks))
                },
                navigationIcon = { BackButton() },
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
            if (books.isEmpty()) {
                ExtensionEmptyState(
                    message = stringResource(
                        if (isPreset) R.string.assistant_extensions_page_empty_presets
                        else R.string.assistant_extensions_page_empty_lorebooks
                    ),
                    buttonText = stringResource(
                        if (isPreset) R.string.assistant_extensions_page_goto_presets
                        else R.string.assistant_extensions_page_goto_lorebooks
                    ),
                    onAction = { navController.navigate(manageScreen) },
                    icon = if (isPreset) HugeIcons.Layers01 else HugeIcons.Book01,
                )
            } else {
                LorebooksContent(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    colors = listColors,
                    lorebooks = books,
                    selectedIds = assistant.lorebookIds,
                    onToggle = { bookId, checked ->
                        vm.update(
                            assistant.copy(
                                lorebookIds = toggleLorebookBinding(assistant.lorebookIds, bookId, checked),
                            ),
                        )
                    },
                    onManage = { navController.navigate(manageScreen) },
                )
            }
        }
    }
}
