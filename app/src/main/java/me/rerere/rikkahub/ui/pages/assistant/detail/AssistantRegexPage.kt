package me.rerere.rikkahub.ui.pages.assistant.detail

import me.rerere.rikkahub.data.export.ImportCompatibility
import me.rerere.rikkahub.data.export.ImportKind
import me.rerere.rikkahub.ui.components.ui.ImportReportState
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Refresh03
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.transformers.DefaultPlaceholderProvider
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.TransformerContext
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.export.SillyTavernRegexImporter
import me.rerere.rikkahub.data.model.AssistantRegex
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.ui.components.message.ChatMessage
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.rikkahub.ui.components.ai.RegexScriptEditor
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TextArea
import me.rerere.rikkahub.ui.components.ui.cardGroupItemShape
import me.rerere.rikkahub.ui.components.ui.longPressReorder
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.theme.ChatFontProvider
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.insertAtCursor
import me.rerere.rikkahub.utils.onError
import me.rerere.rikkahub.utils.onSuccess
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.ui.components.Select
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import sh.calvin.reorderable.ReorderableColumn
import kotlin.uuid.Uuid

/**
 * 助手自己的消息正则（只作用于该助手，执行顺序先于全局正则）。
 * 从「提示词」页拆出来，和全局设置里的「正则」入口对应。
 */
@Composable
fun AssistantRegexPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.setting_page_regex))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        AssistantRegexContent(
            innerPadding = innerPadding,
            assistant = assistant,
            onUpdate = { vm.update(it) },
        )
    }
}

@Composable
private fun AssistantRegexContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val currentAssistant by rememberUpdatedState(assistant)
    val regexImportedMsg = stringResource(R.string.assistant_page_regex_imported)
    val regexImportFailedMsg = stringResource(R.string.assistant_page_regex_import_failed)
    val regexImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error(regexImportFailedMsg)
                    val parsed = SillyTavernRegexImporter.parse(text)
                    parsed to ImportCompatibility.inspect(ImportKind.REGEX, text, parsed.size)
                }
            }
            result.onSuccess { (parsed, report) ->
                if (parsed.isEmpty()) {
                    ImportReportState.show(report)
                    toaster.show(regexImportFailedMsg)
                    return@onSuccess
                }
                onUpdate(currentAssistant.copy(regexes = currentAssistant.regexes + parsed))
                toaster.show(regexImportedMsg.format(parsed.size))
                ImportReportState.show(report)
            }.onFailure {
                toaster.show(regexImportFailedMsg.format(it.message))
                ImportReportState.show(ImportCompatibility.failure(ImportKind.REGEX, it.message))
            }
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(innerPadding)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        var showDeleteAllRegexDialog by remember { mutableStateOf(false) }
        SectionHeader(
            title = stringResource(R.string.assistant_page_regex_title),
            description = stringResource(R.string.assistant_page_regex_desc),
            action = {
                Row {
                    FilledTonalIconButton(
                        onClick = { showDeleteAllRegexDialog = true },
                        enabled = assistant.regexes.isNotEmpty(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(
                            imageVector = HugeIcons.Delete01,
                            contentDescription = stringResource(R.string.assistant_page_regex_delete_all),
                        )
                    }
                    SectionAddButton(
                        onClick = { regexImporter.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        contentDescription = stringResource(R.string.tavern_regex_import),
                        icon = HugeIcons.FileImport,
                    )
                    SectionAddButton(
                        onClick = {
                            onUpdate(
                                assistant.copy(
                                    regexes = assistant.regexes + AssistantRegex(
                                        id = Uuid.random()
                                    )
                                )
                            )
                        }
                    )
                }
            },
        )
        ReorderableColumn(
            list = assistant.regexes,
            onSettle = { fromIndex, toIndex ->
                val regexes = assistant.regexes.toMutableList().apply {
                    add(toIndex, removeAt(fromIndex))
                }
                onUpdate(assistant.copy(regexes = regexes))
            },
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) { index, regex, isDragging ->
            key(regex.id) {
                ReorderableItem(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AssistantRegexCard(
                        regex = regex,
                        onUpdate = onUpdate,
                        assistant = assistant,
                        shape = cardGroupItemShape(index, assistant.regexes.size),
                        modifier = longPressReorder(
                            isDragging = isDragging,
                            enabled = assistant.regexes.size > 1,
                        ),
                    )
                }
            }
        }

        RikkaConfirmDialog(
            show = showDeleteAllRegexDialog,
            title = stringResource(R.string.confirm_delete),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                showDeleteAllRegexDialog = false
                onUpdate(assistant.copy(regexes = emptyList()))
            },
            onDismiss = { showDeleteAllRegexDialog = false },
            text = {
                Text(stringResource(R.string.assistant_page_regex_delete_all_message, assistant.regexes.size))
            },
        )
    }
}

@Composable
private fun AssistantRegexCard(
    regex: AssistantRegex,
    onUpdate: (Assistant) -> Unit,
    assistant: Assistant,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    fun replaceRegex(updated: AssistantRegex) {
        onUpdate(
            assistant.copy(
                regexes = assistant.regexes.map { if (it.id == updated.id) updated else it }
            )
        )
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = CustomColors.listItemColors.containerColor,
    ) {
        Row(
            modifier = Modifier
                .clickable { editing = true }
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = regex.name.ifBlank { stringResource(R.string.extension_content_unnamed) },
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = regex.findRegex,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = JetbrainsMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = regex.enabled,
                onCheckedChange = { replaceRegex(regex.copy(enabled = it)) },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            ItemActionMenu(
                actions = listOf(
                    ItemAction(
                        text = stringResource(R.string.edit),
                        icon = HugeIcons.Edit01,
                        onClick = { editing = true },
                    ),
                    ItemAction(
                        text = stringResource(R.string.delete),
                        icon = HugeIcons.Delete01,
                        destructive = true,
                        onClick = { showDeleteDialog = true },
                    ),
                ),
            )
        }
    }

    if (editing) {
        RegexScriptEditor(
            initial = regex,
            onDismiss = { editing = false },
            onSave = { saved ->
                // 与原内联编辑一致：查找正则去掉首尾空白
                replaceRegex(saved.copy(findRegex = saved.findRegex.trim()))
                editing = false
            },
            onDelete = {
                editing = false
                showDeleteDialog = true
            },
        )
    }

    RikkaConfirmDialog(
        show = showDeleteDialog,
        title = stringResource(R.string.confirm_delete),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showDeleteDialog = false
            onUpdate(assistant.copy(regexes = assistant.regexes.filter { it.id != regex.id }))
        },
        onDismiss = { showDeleteDialog = false },
    ) {
        Text(stringResource(R.string.common_delete_confirm_message, regex.name))
    }
}
