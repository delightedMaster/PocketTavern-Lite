package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.GroupActivationStrategy
import me.rerere.rikkahub.data.model.GroupChat
import me.rerere.rikkahub.ui.components.ai.ExtensionEmptyState
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.rikkahub.utils.plus
import me.rerere.ui.components.RikkaConfirmDialog
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

@Composable
fun GroupChatsPage(vm: GroupChatsVM = koinViewModel()) {
    val settings = vm.settings.collectAsStateWithLifecycle().value
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var editing by remember { mutableStateOf<GroupChat?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<GroupChat?>(null) }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.group_chats_page_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(HugeIcons.Add01, contentDescription = null)
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (settings.groupChats.isEmpty()) {
                item {
                    ExtensionEmptyState(
                        message = stringResource(R.string.group_chats_page_empty),
                        description = stringResource(R.string.extensions_page_group_chats_desc),
                        icon = HugeIcons.MessageAdd01,
                    )
                }
            }
            items(settings.groupChats, key = { it.id }) { group ->
                GroupChatCard(
                    group = group,
                    memberNames = group.memberIds.map { id ->
                        settings.assistants.firstOrNull { it.id == id }?.name.orEmpty()
                    }.filter { it.isNotBlank() },
                    onOpen = {
                        vm.open(group.id) { conversationId ->
                            navigateToChatPage(navController, chatId = conversationId)
                        }
                    },
                    onEdit = { editing = group },
                    onDelete = { deleteTarget = group },
                )
            }
        }
    }

    if (creating) {
        GroupChatEditor(
            initial = GroupChat(),
            assistants = settings.assistants.map { it.id to it.name },
            onDismiss = { creating = false },
            onSave = { group ->
                vm.save(group)
                creating = false
            },
        )
    }
    editing?.let { group ->
        GroupChatEditor(
            initial = group,
            assistants = settings.assistants.map { it.id to it.name },
            onDismiss = { editing = null },
            onSave = { updated ->
                vm.save(updated)
                editing = null
            },
        )
    }
    RikkaConfirmDialog(
        show = deleteTarget != null,
        title = stringResource(R.string.group_chats_page_delete),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            deleteTarget?.let { vm.delete(it.id) }
            deleteTarget = null
        },
        onDismiss = { deleteTarget = null },
    ) {
        Text(deleteTarget?.name.orEmpty())
    }
}

@Composable
private fun GroupChatCard(
    group: GroupChat,
    memberNames: List<String>,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CustomColors.listItemColors.containerColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardGroupIcon(HugeIcons.MessageAdd01)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = group.name.ifBlank { stringResource(R.string.group_chats_page_title) },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = memberNames.joinToString().ifBlank { stringResource(R.string.group_chats_page_members) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onOpen) {
                    Text(stringResource(R.string.group_chats_page_open))
                }
            }
            ItemActionMenu(
                actions = listOf(
                    ItemAction(
                        text = stringResource(R.string.delete),
                        icon = HugeIcons.Delete01,
                        destructive = true,
                        onClick = onDelete,
                    ),
                ),
            )
        }
    }
}

@Composable
private fun GroupChatEditor(
    initial: GroupChat,
    assistants: List<Pair<Uuid, String>>,
    onDismiss: () -> Unit,
    onSave: (GroupChat) -> Unit,
) {
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var strategy by remember(initial.id) { mutableStateOf(initial.activationStrategy) }
    var members by remember(initial.id) { mutableStateOf(initial.memberIds) }
    var muted by remember(initial.id) { mutableStateOf(initial.disabledMemberIds.toSet()) }
    var self by remember(initial.id) { mutableStateOf(initial.allowSelfResponses) }
    var weights by remember(initial.id) { mutableStateOf(initial.speakerWeights) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.group_chats_page_add)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.group_chats_page_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.group_chats_page_strategy))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GroupActivationStrategy.entries.forEach { item ->
                        FilterChip(
                            selected = strategy == item,
                            onClick = { strategy = item },
                            label = { Text(strategyLabel(item)) },
                        )
                    }
                }
                Text(stringResource(R.string.group_chats_page_members))
                assistants.forEach { (id, assistantName) ->
                    val selected = id in members
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { checked ->
                                members = if (checked) members + id else members.filterNot { it == id }
                                if (!checked) muted = muted - id
                            },
                        )
                        Text(
                            text = assistantName.ifBlank { "Character" },
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (selected) {
                            TextButton(onClick = {
                                muted = if (id in muted) muted - id else muted + id
                            }) {
                                Text(
                                    if (id in muted) {
                                        stringResource(R.string.group_chats_page_muted)
                                    } else {
                                        stringResource(R.string.group_chats_page_mute)
                                    },
                                )
                            }
                        }
                    }
                    if (selected && strategy == GroupActivationStrategy.POOLED) {
                        OutlinedTextField(
                            value = (weights[id] ?: 1).toString(),
                            onValueChange = { value ->
                                value.toIntOrNull()?.let { parsed ->
                                    weights = weights + (id to parsed.coerceAtLeast(1))
                                }
                            },
                            label = { Text(stringResource(R.string.group_chats_page_weight)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.group_chats_page_self),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = self, onCheckedChange = { self = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && members.isNotEmpty(),
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            memberIds = members,
                            disabledMemberIds = muted.filter { it in members },
                            activationStrategy = strategy,
                            allowSelfResponses = self,
                            speakerWeights = weights.filterKeys { it in members },
                            enabled = true,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.assistant_page_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun strategyLabel(strategy: GroupActivationStrategy): String = when (strategy) {
    GroupActivationStrategy.NATURAL -> stringResource(R.string.group_strategy_natural)
    GroupActivationStrategy.LIST -> stringResource(R.string.group_strategy_list)
    GroupActivationStrategy.POOLED -> stringResource(R.string.group_strategy_pooled)
    GroupActivationStrategy.MANUAL -> stringResource(R.string.group_strategy_manual)
}
