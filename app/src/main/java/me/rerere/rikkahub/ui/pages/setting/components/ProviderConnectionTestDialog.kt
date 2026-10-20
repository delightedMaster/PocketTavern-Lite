package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderDiagnostics
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.textApiLabel
import me.rerere.rikkahub.ui.theme.extendColors
import me.rerere.rikkahub.utils.UiState
import org.koin.compose.koinInject
import kotlin.time.TimeSource

@Composable
fun ProviderConnectionTestDialog(
    internalProvider: ProviderSetting,
    onDismissRequest: () -> Unit,
) {
    val providerManager = koinInject<ProviderManager>()
    // This scope is disposed with the dialog, cancelling any unfinished request.
    val scope = rememberCoroutineScope()
    val diagnostics = remember(internalProvider) {
        ProviderDiagnostics(providerManager.getProviderByType(internalProvider), internalProvider)
    }
    var models by remember { mutableStateOf(internalProvider.models.filter { it.type == ModelType.CHAT }) }
    var model by remember { mutableStateOf(models.firstOrNull() ?: Model()) }
    var message by remember { mutableStateOf("你好，请简短回复，确认连接正常。") }
    var streaming by remember { mutableStateOf(true) }
    var showModels by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var connectionState: UiState<String> by remember { mutableStateOf(UiState.Idle) }
    var messageState: UiState<String> by remember { mutableStateOf(UiState.Idle) }
    var toolsState: UiState<String> by remember { mutableStateOf(UiState.Idle) }
    var streamingText by remember { mutableStateOf("") }

    fun runTest(setState: (UiState<String>) -> Unit, action: suspend () -> String) {
        if (running) return
        running = true
        setState(UiState.Loading)
        scope.launch {
            val started = TimeSource.Monotonic.markNow()
            try {
                val result = action()
                setState(UiState.Success("耗时 ${started.elapsedNow().inWholeMilliseconds} ms\n$result"))
            } catch (e: TimeoutCancellationException) {
                setState(UiState.Error(IllegalStateException("测试超过 90 秒，请检查网络或稍后重试", e)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setState(UiState.Error(e))
            } finally {
                running = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("连接与消息测试") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("${internalProvider.name} · ${internalProvider.textApiLabel}")
                Text(
                    "使用当前填写的配置。读取模型列表只检查该接口；发送消息才能确认所选模型可用，会产生正常 API 用量。测试不会保存为聊天。",
                    style = MaterialTheme.typography.bodySmall,
                )
                FilledTonalButton(
                    enabled = !running,
                    onClick = {
                        runTest({ connectionState = it }) {
                            val fetched = diagnostics.listModels().filter { it.type == ModelType.CHAT }
                            models = (models + fetched).distinctBy { it.modelId }
                            if (model.modelId.isBlank()) model = models.firstOrNull() ?: model
                            "模型列表接口已响应，返回 ${fetched.size} 个聊天模型。若列表为空，可手填模型 ID 测试。"
                        }
                    },
                ) { Text("测试连接 / 读取模型") }
                TestResultItem("连接", connectionState, (connectionState as? UiState.Success)?.data.orEmpty())
                OutlinedTextField(
                    value = model.modelId,
                    onValueChange = { value ->
                        model = models.firstOrNull { it.modelId == value.trim() }
                            ?: Model(modelId = value.trim())
                        messageState = UiState.Idle
                        toolsState = UiState.Idle
                        streamingText = ""
                    },
                    enabled = !running,
                    label = { Text("模型 ID（可手填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (models.isNotEmpty()) {
                    Column {
                        TextButton(enabled = !running, onClick = { showModels = true }) { Text("从模型列表选择") }
                        DropdownMenu(expanded = showModels, onDismissRequest = { showModels = false }) {
                            models.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.modelId) },
                                    onClick = {
                                        model = item
                                        showModels = false
                                        messageState = UiState.Idle
                                        toolsState = UiState.Idle
                                        streamingText = ""
                                    },
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it; messageState = UiState.Idle; streamingText = "" },
                    enabled = !running,
                    label = { Text("测试消息") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("流式回复", modifier = Modifier.weight(1f))
                    Switch(
                        checked = streaming,
                        onCheckedChange = { streaming = it; messageState = UiState.Idle; streamingText = "" },
                        enabled = !running,
                    )
                }
                FilledTonalButton(
                    enabled = !running && model.modelId.isNotBlank() && message.isNotBlank(),
                    onClick = {
                        val selected = model
                        val prompt = message
                        val useStreaming = streaming
                        streamingText = ""
                        runTest({ messageState = it }) {
                            val reply = diagnostics.send(selected, prompt, useStreaming) { streamingText = it }
                            "模型：${selected.modelId}\n$reply"
                        }
                    },
                ) { Text("发送测试消息") }
                TestResultItem(
                    if (streaming) "流式消息" else "普通消息",
                    messageState,
                    (messageState as? UiState.Success)?.data.orEmpty(),
                )
                if (streamingText.isNotBlank() && messageState !is UiState.Success) {
                    SelectionContainer { Text(streamingText) }
                }
                TextButton(
                    enabled = !running && model.modelId.isNotBlank(),
                    onClick = {
                        val selected = model
                        runTest({ toolsState = it }) { diagnostics.testTools(selected) }
                    },
                ) { Text("单独测试工具调用（可选）") }
                TestResultItem("工具调用", toolsState, (toolsState as? UiState.Success)?.data.orEmpty())
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text(if (running) "停止并关闭" else "完成") }
        },
    )
}

@Composable
private fun TestResultItem(
    label: String,
    state: UiState<String>,
    resultText: String
) {
    var showErrorSheet by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(120.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        when (state) {
            is UiState.Idle -> Text(
                text = "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            is UiState.Loading -> LinearWavyProgressIndicator(modifier = Modifier.weight(1f))
            is UiState.Success -> Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.extendColors.green6
                )
                if (resultText.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            text = resultText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            is UiState.Error -> Text(
                text = state.error.message ?: stringResource(R.string.search_detail_unknown_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendColors.red6,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable { showErrorSheet = true }
            )
        }
    }

    if (showErrorSheet && state is UiState.Error) {
        val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
        val stackTrace = remember(state.error) {
            state.error.stackTraceToString()
        }
        ModalBottomSheet(
            onDismissRequest = { showErrorSheet = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = state.error.message ?: stringResource(R.string.search_detail_unknown_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.extendColors.red6
                )
                Text(
                    text = stackTrace,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
