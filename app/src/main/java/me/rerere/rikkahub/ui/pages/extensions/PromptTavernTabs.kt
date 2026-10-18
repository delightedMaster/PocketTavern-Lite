package me.rerere.rikkahub.ui.pages.extensions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.transformers.MacroEngine
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.authorNoteEntriesOrLegacy
import me.rerere.rikkahub.data.export.SillyTavernRegexImporter
import me.rerere.rikkahub.data.model.AssistantRegex
import me.rerere.rikkahub.data.model.AuthorNoteEntry
import me.rerere.rikkahub.data.model.AuthorNotePosition
import me.rerere.rikkahub.data.model.RegexGroup
import me.rerere.rikkahub.data.model.RegexGroupSource
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import kotlin.uuid.Uuid

@Composable
internal fun AuthorNoteTab(
    settings: Settings,
    onUpdate: ((Settings) -> Settings) -> Unit,
) {
    val entries = settings.authorNoteEntriesOrLegacy()
    var editing by remember { mutableStateOf<AuthorNoteEntry?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CardGroup {
            item(
                headlineContent = { Text(stringResource(R.string.prompt_page_author_note_enable)) },
                supportingContent = { Text(stringResource(R.string.prompt_page_author_note_enable_desc)) },
                trailingContent = {
                    Switch(
                        checked = settings.authorNoteEnabled,
                        onCheckedChange = { enabled ->
                            onUpdate { it.copy(authorNoteEnabled = enabled) }
                        },
                    )
                },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.prompt_page_author_note_entries), style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = {
                editing = AuthorNoteEntry(id = "custom:${Uuid.random()}", name = "")
            }) {
                Text(stringResource(R.string.prompt_page_author_note_add))
            }
        }
        if (entries.isEmpty()) {
            Text(
                stringResource(R.string.prompt_page_author_note_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        entries.forEach { entry ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CustomColors.listItemColors.containerColor),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.name.ifBlank { stringResource(R.string.prompt_page_author_note_unnamed) })
                        Text(
                            entry.content.replace("\n", " ").take(80),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                    Switch(
                        checked = entry.enabled,
                        onCheckedChange = { enabled ->
                            onUpdate { current ->
                                current.copy(
                                    authorNoteEntries = current.authorNoteEntriesOrLegacy().map {
                                        if (it.id == entry.id) it.copy(enabled = enabled) else it
                                    }
                                )
                            }
                        },
                    )
                    TextButton(onClick = { editing = entry }) {
                        Text(stringResource(R.string.prompt_page_edit_entry))
                    }
                }
            }
        }
        Text(stringResource(R.string.prompt_page_macro_catalog), style = MaterialTheme.typography.titleSmall)
        MacroEngine.macroCatalog.groupBy { it.group }.forEach { (group, items) ->
            Text(group, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            items.forEach { item ->
                Text(
                    "{{${item.syntax}}}  ${item.description}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    editing?.let { entry ->
        AuthorNoteEditor(
            initial = entry,
            onDismiss = { editing = null },
            onSave = { saved ->
                onUpdate { current ->
                    val currentEntries = current.authorNoteEntriesOrLegacy()
                    val exists = currentEntries.any { it.id == saved.id }
                    current.copy(
                        authorNoteEntries = if (exists) {
                            currentEntries.map { if (it.id == saved.id) saved else it }
                        } else {
                            currentEntries + saved
                        }
                    )
                }
                editing = null
            },
            onDelete = {
                onUpdate { current ->
                    current.copy(
                        authorNoteEntries = current.authorNoteEntriesOrLegacy().filterNot { it.id == entry.id }
                    )
                }
                editing = null
            },
        )
    }
}

@Composable
private fun AuthorNoteEditor(
    initial: AuthorNoteEntry,
    onDismiss: () -> Unit,
    onSave: (AuthorNoteEntry) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var content by remember(initial.id) { mutableStateOf(initial.content) }
    var position by remember(initial.id) { mutableStateOf(initial.position) }
    var depth by remember(initial.id) { mutableStateOf(initial.depth.toString()) }
    var role by remember(initial.id) { mutableStateOf(initial.role) }
    var interval by remember(initial.id) { mutableStateOf(initial.interval.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.prompt_page_author_note_edit)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.prompt_page_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.prompt_page_author_note_content)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
                Text(stringResource(R.string.prompt_page_injection_position), style = MaterialTheme.typography.labelLarge)
                AuthorNotePosition.entries.forEach { option ->
                    FilterChip(
                        selected = position == option,
                        onClick = { position = option },
                        label = { Text(authorNotePositionLabel(option)) },
                    )
                }
                if (position == AuthorNotePosition.IN_CHAT) {
                    OutlinedTextField(
                        value = depth,
                        onValueChange = { depth = it },
                        label = { Text(stringResource(R.string.prompt_page_author_note_depth)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
                Text(stringResource(R.string.prompt_page_injection_role), style = MaterialTheme.typography.labelLarge)
                listOf(MessageRole.SYSTEM, MessageRole.USER, MessageRole.ASSISTANT).forEach { option ->
                    FilterChip(
                        selected = role == option,
                        onClick = { role = option },
                        label = { Text(option.name) },
                    )
                }
                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it },
                    label = { Text(stringResource(R.string.prompt_page_author_note_interval)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    initial.copy(
                        name = name,
                        content = content,
                        position = position,
                        depth = depth.toIntOrNull()?.coerceAtLeast(0) ?: initial.depth,
                        role = role,
                        interval = interval.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        enabled = true,
                    )
                )
            }) { Text(stringResource(R.string.prompt_page_confirm)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun authorNotePositionLabel(position: AuthorNotePosition): String = when (position) {
    AuthorNotePosition.BEFORE_PROMPT -> stringResource(R.string.prompt_page_author_note_before)
    AuthorNotePosition.IN_PROMPT -> stringResource(R.string.prompt_page_author_note_in_prompt)
    AuthorNotePosition.IN_CHAT -> stringResource(R.string.prompt_page_author_note_in_chat)
}

@Composable
internal fun RegexScriptsTab(
    groups: List<RegexGroup>,
    onUpdate: (List<RegexGroup>) -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(groups)
    val importedMsg = stringResource(R.string.assistant_page_regex_imported)
    val failedMsg = stringResource(R.string.export_import_failed)
    val customName = stringResource(R.string.tavern_regex_custom_folder)
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error(failedMsg)
                    val parsed = SillyTavernRegexImporter.parse(text)
                    if (parsed.isEmpty()) error(failedMsg)
                    parsed
                }
            }
            result.onSuccess { parsed ->
                val existing = current.firstOrNull { it.source == RegexGroupSource.CUSTOM }
                val updated = if (existing != null) {
                    current.map {
                        if (it.id == existing.id) it.copy(regexes = it.regexes + parsed) else it
                    }
                } else {
                    listOf(
                        RegexGroup(name = customName, source = RegexGroupSource.CUSTOM, regexes = parsed)
                    ) + current
                }
                onUpdate(updated)
                toaster.show(importedMsg.format(parsed.size))
            }.onFailure {
                toaster.show(failedMsg.format(it.message))
            }
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.tavern_regex_intro),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                Text(stringResource(R.string.tavern_regex_import))
            }
        }
        if (groups.isEmpty()) {
            Text(
                stringResource(R.string.tavern_regex_empty_global),
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(groups, key = { it.id }) { group ->
                    RegexGroupCard(group = group, onChange = { updated ->
                        onUpdate(groups.map { if (it.id == updated.id) updated else it })
                    })
                }
            }
        }
    }
}

@Composable
private fun RegexGroupCard(
    group: RegexGroup,
    onChange: (RegexGroup) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CustomColors.listItemColors.containerColor),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(group.name.ifBlank { stringResource(R.string.tavern_regex_unnamed_group) })
            Text(
                stringResource(R.string.tavern_regex_group_count, group.regexes.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            group.regexes.forEach { regex ->
                RegexScriptRow(regex = regex, onChange = { updated ->
                    onChange(group.copy(regexes = group.regexes.map { if (it.id == updated.id) updated else it }))
                })
            }
        }
    }
}

@Composable
private fun RegexScriptRow(
    regex: AssistantRegex,
    onChange: (AssistantRegex) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(regex.name.ifBlank { regex.findRegex }, modifier = Modifier.weight(1f))
            Switch(checked = regex.enabled, onCheckedChange = { onChange(regex.copy(enabled = it)) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = regex.visualOnly,
                onClick = { onChange(regex.copy(visualOnly = !regex.visualOnly, promptOnly = false)) },
                label = { Text(stringResource(R.string.assistant_page_regex_visual_only_short)) },
            )
            FilterChip(
                selected = regex.promptOnly,
                onClick = { onChange(regex.copy(promptOnly = !regex.promptOnly, visualOnly = false)) },
                label = { Text(stringResource(R.string.assistant_page_regex_prompt_only_short)) },
            )
        }
        Text(
            buildString {
                append(regex.affectingScope.joinToString { it.name })
                if (regex.minDepth > 0 || regex.maxDepth > 0) {
                    append("  ")
                    append(regex.minDepth)
                    append("–")
                    append(if (regex.maxDepth > 0) regex.maxDepth.toString() else "∞")
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
