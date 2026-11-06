package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.AssistantRegex
import me.rerere.rikkahub.data.model.parseTavernRegex
import me.rerere.rikkahub.data.model.replaceRegexes

/**
 * 正则规则编辑弹窗，全局正则（PromptTavernTabs）与助手「消息正则规则」共用。
 * 保存时回调编辑后的 [AssistantRegex]，删除时由调用方决定如何确认。
 */
@Composable
fun RegexScriptEditor(
    initial: AssistantRegex,
    onDismiss: () -> Unit,
    onSave: (AssistantRegex) -> Unit,
    onDelete: () -> Unit,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var sample by remember(initial.id) { mutableStateOf("") }
    val compileError = remember(draft.findRegex) {
        // 与运行时一致：复用 parseTavernRegex，连 /pattern/flags 的修饰符也一并校验
        if (draft.findRegex.isBlank()) null else parseTavernRegex(draft.findRegex).exceptionOrNull()?.message
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(draft.name.ifBlank { stringResource(R.string.extension_content_unnamed) }) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text(stringResource(R.string.assistant_page_regex_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = draft.findRegex,
                    onValueChange = { draft = draft.copy(findRegex = it) },
                    label = { Text(stringResource(R.string.assistant_page_regex_find_regex)) },
                    isError = compileError != null,
                    supportingText = compileError?.let { { Text("正则无效：$it") } },
                    placeholder = { Text(stringResource(R.string.assistant_page_regex_find_example)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                OutlinedTextField(
                    value = draft.replaceString,
                    onValueChange = { draft = draft.copy(replaceString = it) },
                    label = { Text(stringResource(R.string.assistant_page_regex_replace_string)) },
                    placeholder = { Text(stringResource(R.string.assistant_page_regex_replace_example)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 12,
                )
                Text(stringResource(R.string.assistant_page_regex_affecting_scopes), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistantAffectScope.entries.forEach { scope ->
                        val selected = scope in draft.affectingScope
                        FilterChip(
                            selected = selected,
                            onClick = {
                                draft = draft.copy(
                                    affectingScope = if (selected) draft.affectingScope - scope else draft.affectingScope + scope
                                )
                            },
                            label = { Text(scope.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.visualOnly,
                        onClick = { draft = draft.copy(visualOnly = !draft.visualOnly, promptOnly = false) },
                        label = { Text(stringResource(R.string.assistant_page_regex_visual_only_short)) },
                    )
                    FilterChip(
                        selected = draft.promptOnly,
                        onClick = { draft = draft.copy(promptOnly = !draft.promptOnly, visualOnly = false) },
                        label = { Text(stringResource(R.string.assistant_page_regex_prompt_only_short)) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = if (draft.minDepth > 0) draft.minDepth.toString() else "",
                        onValueChange = { draft = draft.copy(minDepth = it.toIntOrNull()?.coerceAtLeast(0) ?: 0) },
                        label = { Text(stringResource(R.string.assistant_page_regex_min_depth)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = if (draft.maxDepth > 0) draft.maxDepth.toString() else "",
                        onValueChange = { draft = draft.copy(maxDepth = it.toIntOrNull()?.coerceAtLeast(0) ?: 0) },
                        label = { Text(stringResource(R.string.assistant_page_regex_max_depth)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                // 粘贴一段模型原文，直接看这条规则替换后的结果，排查「为什么没生效」
                OutlinedTextField(
                    value = sample,
                    onValueChange = { sample = it },
                    label = { Text("测试文本（粘贴模型原始回复）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 8,
                )
                if (sample.isNotEmpty()) {
                    val result = sample.replaceRegexes(
                        regexes = listOf(draft.copy(enabled = true)),
                        scope = AssistantAffectScope.ASSISTANT,
                        visual = !draft.promptOnly,
                    )
                    Text(
                        when {
                            AssistantAffectScope.ASSISTANT !in draft.affectingScope -> "未勾选 Assistant 范围，不会作用于 AI 回复"
                            result == sample -> "没有匹配到任何内容"
                            else -> result
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) { Text(stringResource(R.string.prompt_page_confirm)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
