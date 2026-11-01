package me.rerere.rikkahub.ui.components.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.uuid.Uuid
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Book01
import me.rerere.hugeicons.stroke.Layers01
import me.rerere.hugeicons.stroke.MagicWand01
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.skills.SkillManager
import me.rerere.rikkahub.data.skills.SkillMetadata
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.LorebookKind
import me.rerere.rikkahub.data.model.lorebooksOfKind
import me.rerere.rikkahub.data.model.toggleLorebookBinding
import me.rerere.rikkahub.ui.components.ai.ExtensionEmptyState
import me.rerere.rikkahub.ui.components.ai.LorebooksContent
import me.rerere.rikkahub.ui.components.ai.ModeInjectionsContent
import me.rerere.rikkahub.ui.components.ai.QuickMessagesContent
import me.rerere.rikkahub.ui.components.ai.SkillsContent
import org.koin.compose.koinInject

internal enum class ExtensionTab(
    override val icon: ImageVector,
    @param:StringRes private val labelRes: Int,
) : PagerTab {
    QUICK_MESSAGES(HugeIcons.Zap, R.string.extension_selector_tab_quick_messages),
    MODE_INJECTIONS(HugeIcons.MagicWand01, R.string.extension_selector_tab_mode_injections),
    LOREBOOKS(HugeIcons.Book01, R.string.extension_selector_tab_lorebooks),
    PRESETS(HugeIcons.Layers01, R.string.extension_selector_tab_presets),
    SKILLS(HugeIcons.Puzzle, R.string.extension_selector_tab_skills);

    override val label: String
        @Composable get() = stringResource(labelRes)
}


@Composable
fun ExtensionSelector(
    modifier: Modifier = Modifier,
    assistant: Assistant,
    settings: Settings,
    onUpdate: (Assistant) -> Unit,
    onNavigateToQuickMessages: () -> Unit = {},
    onNavigateToPrompts: () -> Unit = {},
    onNavigateToLorebooks: () -> Unit = {},
    onNavigateToPresets: () -> Unit = {},
    onNavigateToSkills: () -> Unit = {},
) {
    val skillManager: SkillManager = koinInject()
    var skills by remember { mutableStateOf<List<SkillMetadata>>(emptyList()) }

    LaunchedEffect(Unit) {
        // 打开扩展面板时清理运行时被删除的技能（残留的 enabledSkills 引用），
        // prune 顺带返回现存技能列表，避免重复读盘
        skills = skillManager.pruneOrphanedEnabledSkills()
    }

    val pagerState = rememberPagerState { ExtensionTab.entries.size }

    Column(
        modifier = modifier
    ) {
        PagerTabs(
            pagerState = pagerState,
            tabs = ExtensionTab.entries,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            val tab = ExtensionTab.entries[page]
            when (tab) {
                ExtensionTab.QUICK_MESSAGES -> {
                    if (settings.quickMessages.isNotEmpty()) {
                        QuickMessagesContent(
                            quickMessages = settings.quickMessages,
                            selectedIds = assistant.quickMessageIds,
                            onToggle = { id, checked ->
                                val newIds = if (checked) {
                                    assistant.quickMessageIds + id
                                } else {
                                    assistant.quickMessageIds - id
                                }
                                onUpdate(assistant.copy(quickMessageIds = newIds))
                            },
                            onManage = onNavigateToQuickMessages,
                        )
                    } else {
                        ExtensionEmptyState(
                            message = stringResource(R.string.extension_selector_quick_messages_empty),
                            buttonText = stringResource(R.string.extension_selector_go_to_extensions),
                            onAction = onNavigateToQuickMessages,
                            icon = tab.icon,
                        )
                    }
                }

                ExtensionTab.MODE_INJECTIONS -> {
                    if (settings.modeInjections.isNotEmpty()) {
                        ModeInjectionsContent(
                            modeInjections = settings.modeInjections,
                            selectedIds = assistant.modeInjectionIds,
                            onToggle = { id, checked ->
                                val newIds = if (checked) {
                                    assistant.modeInjectionIds + id
                                } else {
                                    assistant.modeInjectionIds - id
                                }
                                onUpdate(assistant.copy(modeInjectionIds = newIds))
                            },
                            onManage = onNavigateToPrompts,
                        )
                    } else {
                        ExtensionEmptyState(
                            message = stringResource(R.string.extension_selector_mode_injections_empty),
                            buttonText = stringResource(R.string.extension_selector_go_to_extensions),
                            onAction = onNavigateToPrompts,
                            icon = tab.icon,
                        )
                    }
                }

                ExtensionTab.LOREBOOKS -> {
                    LorebookBindingTab(
                        lorebooks = lorebooksOfKind(settings.lorebooks, LorebookKind.WORLD_BOOK),
                        selectedIds = assistant.lorebookIds,
                        emptyMessage = stringResource(R.string.extension_selector_lorebooks_empty),
                        manageLabel = stringResource(R.string.extension_selector_go_to_lorebooks),
                        icon = tab.icon,
                        onToggle = { id, checked ->
                            onUpdate(assistant.withLorebookBinding(id, checked))
                        },
                        onManage = onNavigateToLorebooks,
                    )
                }

                ExtensionTab.PRESETS -> {
                    LorebookBindingTab(
                        lorebooks = lorebooksOfKind(settings.lorebooks, LorebookKind.PRESET),
                        selectedIds = assistant.lorebookIds,
                        emptyMessage = stringResource(R.string.extension_selector_presets_empty),
                        manageLabel = stringResource(R.string.extension_selector_go_to_presets),
                        icon = tab.icon,
                        onToggle = { id, checked ->
                            onUpdate(assistant.withLorebookBinding(id, checked))
                        },
                        onManage = onNavigateToPresets,
                    )
                }

                ExtensionTab.SKILLS -> {
                    if (skills.isNotEmpty()) {
                        SkillsContent(
                            skills = skills,
                            enabledSkills = assistant.enabledSkills,
                            onToggle = { name, checked ->
                                val newSkills = if (checked) {
                                    assistant.enabledSkills + name
                                } else {
                                    assistant.enabledSkills - name
                                }
                                onUpdate(assistant.copy(enabledSkills = newSkills))
                            },
                            onManage = onNavigateToSkills,
                        )
                    } else {
                        ExtensionEmptyState(
                            message = stringResource(R.string.extension_selector_skills_empty),
                            buttonText = stringResource(R.string.extension_selector_go_to_skills),
                            onAction = onNavigateToSkills,
                            icon = tab.icon,
                        )
                    }
                }
            }
        }
    }
}

// 世界书和预设共用 lorebookIds。只增删当前这一条，另一类的绑定保持不变。
private fun Assistant.withLorebookBinding(id: Uuid, checked: Boolean): Assistant =
    copy(lorebookIds = toggleLorebookBinding(lorebookIds, id, checked))

@Composable
private fun LorebookBindingTab(
    lorebooks: List<Lorebook>,
    selectedIds: Set<Uuid>,
    emptyMessage: String,
    manageLabel: String,
    icon: ImageVector,
    onToggle: (Uuid, Boolean) -> Unit,
    onManage: () -> Unit,
) {
    if (lorebooks.isNotEmpty()) {
        LorebooksContent(
            lorebooks = lorebooks,
            selectedIds = selectedIds,
            onToggle = onToggle,
            onManage = onManage,
        )
    } else {
        ExtensionEmptyState(
            message = emptyMessage,
            buttonText = manageLabel,
            onAction = onManage,
            icon = icon,
        )
    }
}
