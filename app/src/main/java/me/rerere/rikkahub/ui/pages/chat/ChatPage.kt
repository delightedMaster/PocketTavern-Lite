package me.rerere.rikkahub.ui.pages.chat

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ui.toast.ToastType
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.GroupActivationStrategy
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.service.PendingSummaryReview
import me.rerere.rikkahub.ui.components.ai.ChatAttachmentPickerActions
import me.rerere.rikkahub.ui.components.ai.ChatInput
import me.rerere.rikkahub.ui.components.ai.FilesPicker
import me.rerere.rikkahub.ui.components.message.LocalHtmlCardAllowlist
import me.rerere.rikkahub.ui.components.message.LocalTavernCardBridge
import me.rerere.rikkahub.ui.components.message.TavernCardBridge
import me.rerere.rikkahub.ui.components.ai.SearchMode
import me.rerere.rikkahub.ui.components.ai.rememberChatAttachmentPickerActions
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.utils.base64Decode
import me.rerere.rikkahub.utils.navigateToChatPage
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

@Composable
fun ChatPage(id: Uuid, text: String?, files: List<Uri>, nodeId: Uuid? = null) {
    val vm: ChatVM = koinViewModel(
        parameters = {
            parametersOf(id.toString())
        }
    )
    val filesManager: FilesManager = koinInject()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    val setting by vm.settings.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val processingStatus by vm.processingStatus.collectAsStateWithLifecycle()
    val currentChatModel by vm.currentChatModel.collectAsStateWithLifecycle()
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()

    // 新会话发出第一条消息前，跟随抽屉当前选中的文件夹（#2133）
    val drawerVm: ChatDrawerVM = koinViewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity)
    val selectedFolderId by drawerVm.selectedFolderId.collectAsStateWithLifecycle()
    LaunchedEffect(selectedFolderId) {
        vm.updateDraftFolder(selectedFolderId)
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Handle back press when drawer is open
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch {
            drawerState.close()
        }
    }

    // Clear input focus so popup transitions cannot reopen the keyboard.
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            focusManager.clearFocus(force = true)
            softwareKeyboardController?.hide()
        }
    }

    val windowAdaptiveInfo = currentWindowDpSize()
    val isBigScreen =
        windowAdaptiveInfo.width > windowAdaptiveInfo.height && windowAdaptiveInfo.width >= 1100.dp

    // 进入大屏（永久抽屉）模式时重置抽屉状态为关闭，
    // 避免从横屏旋转回竖屏后，模态抽屉残留为打开状态且无法关闭（#1304）
    LaunchedEffect(isBigScreen) {
        if (isBigScreen && drawerState.isOpen) {
            drawerState.close()
        }
    }

    val inputState = vm.inputState

    // 初始化输入状态（处理传入的 files 和 text 参数）
    LaunchedEffect(files, text) {
        if (files.isNotEmpty()) {
            val localFiles = filesManager.createChatFilesByContents(files)
            val contentTypes = files.mapNotNull { file ->
                filesManager.getFileMimeType(file)
            }
            val parts = buildList {
                localFiles.forEachIndexed { index, file ->
                    val type = contentTypes.getOrNull(index)
                    if (type?.startsWith("image/") == true) {
                        add(UIMessagePart.Image(url = file.toString()))
                    } else if (type?.startsWith("video/") == true) {
                        add(UIMessagePart.Video(url = file.toString()))
                    } else if (type?.startsWith("audio/") == true) {
                        add(UIMessagePart.Audio(url = file.toString()))
                    }
                }
            }
            inputState.messageContent = parts
        }
        text?.base64Decode()?.let { decodedText ->
            if (decodedText.isNotEmpty()) {
                inputState.setMessageText(decodedText)
            }
        }
    }

    val chatListState = rememberLazyListState()
    LaunchedEffect(nodeId, conversation.messageNodes.size) {
        if (!vm.chatListInitialized && conversation.messageNodes.isNotEmpty()) {
            if (nodeId != null) {
                val index = conversation.messageNodes.indexOfFirst { it.id == nodeId }
                if (index >= 0) {
                    chatListState.scrollToItem(index)
                }
            } else {
                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
            }
            vm.chatListInitialized = true
        }
    }

    when {
        isBigScreen -> {
            PermanentNavigationDrawer(
                drawerContent = {
                    ChatDrawerContent(
                        navController = navController,
                        current = conversation,
                        vm = vm,
                        settings = setting
                    )
                }
            ) {
                ChatPageContent(
                    inputState = inputState,
                    loadingJob = loadingJob,
                    processingStatus = processingStatus,
                    setting = setting,
                    conversation = conversation,
                    drawerState = drawerState,
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    assistant = assistant,
                    currentChatModel = currentChatModel,
                    bigScreen = true,
                    errors = errors,
                    onDismissError = { vm.dismissError(it) },
                    onClearAllErrors = { vm.clearAllErrors() },
                )
            }
        }

        else -> {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ChatDrawerContent(
                        navController = navController,
                        current = conversation,
                        vm = vm,
                        settings = setting
                    )
                }
            ) {
                ChatPageContent(
                    inputState = inputState,
                    loadingJob = loadingJob,
                    processingStatus = processingStatus,
                    setting = setting,
                    conversation = conversation,
                    drawerState = drawerState,
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    assistant = assistant,
                    currentChatModel = currentChatModel,
                    bigScreen = false,
                    errors = errors,
                    onDismissError = { vm.dismissError(it) },
                    onClearAllErrors = { vm.clearAllErrors() },
                )
            }
            BackHandler(drawerState.isOpen) {
                scope.launch { drawerState.close() }
            }
        }
    }
}

@Composable
private fun ChatPageContent(
    inputState: ChatInputState,
    loadingJob: Job?,
    processingStatus: String? = null,
    setting: Settings,
    bigScreen: Boolean,
    conversation: Conversation,
    drawerState: DrawerState,
    navController: Navigator,
    vm: ChatVM,
    chatListState: LazyListState,
    assistant: Assistant,
    currentChatModel: Model?,
    errors: List<ChatError>,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current

    var previewMode by rememberSaveable { mutableStateOf(false) }
    val hazeState = rememberHazeState()
    var showFilesSheet by remember { mutableStateOf(false) }
    var showBindQuickReplies by remember { mutableStateOf(false) }
    val group = conversation.config?.groupId?.let { id ->
        setting.groupChats.firstOrNull { it.id == id && it.enabled }
    }
    val speakerChoices = if (group?.activationStrategy == GroupActivationStrategy.MANUAL) {
        group.memberIds.mapNotNull { memberId ->
            if (memberId in group.disabledMemberIds) {
                null
            } else {
                setting.assistants.firstOrNull { it.id == memberId }?.let { member ->
                    member.id to member.name.ifBlank { "Character" }
                }
            }
        }
    } else {
        emptyList()
    }
    val cardBridge = remember(vm) {
        TavernCardBridge { messageId, requestJson -> vm.handleCard(messageId, requestJson) }
    }
    val attachmentPickerActions = rememberChatAttachmentPickerActions(
        inputState = inputState,
        setting = setting,
        onAttachmentAdded = { showFilesSheet = false },
    )
    val allowAudioVideoAttachments =
        currentChatModel?.findProvider(setting.providers) is ProviderSetting.Google



    val pendingSummary by vm.pendingSummaryReview.collectAsStateWithLifecycle()
    val review = pendingSummary?.takeIf { it.conversationId == conversation.id }
    if (review != null) {
        SummaryReviewDialog(
            pending = review,
            onConfirm = vm::confirmSummaryReview,
            onCancel = vm::cancelSummaryReview,
        )
    }

    CompositionLocalProvider(
        LocalTavernCardBridge provides cardBridge,
        LocalHtmlCardAllowlist provides setting.htmlCardAllowlist,
    ) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        AssistantBackground(setting = setting, modifier = Modifier.hazeSource(hazeState))
        Scaffold(
            topBar = {
                TopBar(
                    settings = setting,
                    assistant = assistant,
                    chatModel = currentChatModel,
                    conversation = conversation,
                    bigScreen = bigScreen,
                    drawerState = drawerState,
                    previewMode = previewMode,
                    onNewChat = {
                        navigateToChatPage(navController)
                    },
                    onClickMenu = {
                        previewMode = !previewMode
                    },
                    onUpdateTitle = {
                        vm.updateTitle(it)
                    }
                )
            },
            bottomBar = {
                val messageQueue by vm.messageQueue.collectAsStateWithLifecycle()
                ChatInput(
                    state = inputState,
                    messageQueue = messageQueue,
                    onRemoveQueuedMessage = vm::removeQueuedMessage,
                    onBeginEditQueuedMessage = vm::beginEditQueuedMessage,
                    onFinishEditQueuedMessage = vm::finishEditQueuedMessage,
                    onResumeMessageQueue = vm::resumeMessageQueue,
                    loading = loadingJob != null,
                    settings = setting,
                    assistant = assistant,
                    chatModel = currentChatModel,
                    hazeState = hazeState,
                            onCancelClick = {
                        vm.stopGeneration()
                    },
                    onUpdateSearchMode = { mode ->
                        vm.updateSearch(
                            enableWebSearch = mode == SearchMode.LOCAL,
                            builtInSearch = mode == SearchMode.BUILT_IN,
                        )
                    },
                    onSendClick = {
                        if (currentChatModel == null) {
                            toaster.show("请先选择模型", type = ToastType.Error)
                            return@ChatInput
                        }
                        if (inputState.isEditing()) {
                            vm.handleMessageEdit(
                                parts = inputState.getContents(),
                                messageId = inputState.editingMessage!!,
                            )
                        } else {
                            vm.handleMessageSend(inputState.getContents())
                            scope.launch {
                                delay(100.milliseconds)
                                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                            }
                        }
                        inputState.clearInput()
                    },
                    onLongSendClick = {
                        if (inputState.isEditing()) {
                            vm.handleMessageEdit(
                                parts = inputState.getContents(),
                                messageId = inputState.editingMessage!!,
                            )
                        } else {
                            vm.handleMessageSend(content = inputState.getContents(), answer = false)
                            scope.launch {
                                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                            }
                        }
                        inputState.clearInput()
                    },
                    onUpdateChatModel = vm::setChatModel,
                    onUpdateAssistant = vm::updateAssistant,
                    onUpdateSearchService = { index ->
                        vm.updateSettings { it.copy(searchServiceSelected = index) }
                    },
                    onMoreClick = {
                        showFilesSheet = true
                    },
                    extraQuickReplyIds = conversation.config?.quickReplyIds.orEmpty(),
                    onRunQuickReply = vm::runQuickReply,
                    onBindQuickReplies = { showBindQuickReplies = true },
                    speakerChoices = speakerChoices,
                    selectedSpeakerId = conversation.config?.groupSpeakerId,
                    onSelectSpeaker = vm::setGroupSpeaker,
                )
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            ChatList(
                innerPadding = innerPadding,
                conversation = conversation,
                state = chatListState,
                loading = loadingJob != null,
                processingStatus = processingStatus,
                previewMode = previewMode,
                settings = setting,
                hazeState = hazeState,
                errors = errors,
                onDismissError = onDismissError,
                onClearAllErrors = onClearAllErrors,
                onRegenerate = {
                    vm.regenerateAtMessage(it)
                },
                onEdit = {
                    inputState.editingMessage = it.id
                    inputState.setContents(it.parts)
                },
                onForkMessage = {
                    scope.launch {
                        val fork = vm.forkMessage(message = it)
                        navigateToChatPage(navController, chatId = fork.id)
                    }
                },
                onRestoreArchived = vm::restoreArchived,
                onDelete = {
                    if (loadingJob != null) {
                        vm.showDeleteBlockedWhileGeneratingError()
                    } else {
                        vm.deleteMessage(it)
                    }
                },
                onUpdateMessage = { newNode ->
                    vm.selectMessageNode(newNode.id, newNode.selectIndex)
                },
                onClickSuggestion = { suggestion ->
                    inputState.editingMessage = null
                    inputState.setMessageText(suggestion)
                },
                onJumpToMessage = { index ->
                    previewMode = false
                    scope.launch {
                        chatListState.requestScrollToItem(index)
                    }
                },
                onToolApproval = { toolCallId, approved, reason ->
                    vm.handleToolApproval(toolCallId, approved, reason)
                },
                onToolAnswer = { toolCallId, answer ->
                    vm.handleToolAnswer(toolCallId, answer)
                },
                onConversationSystemPromptChange = { newPrompt ->
                    vm.updateConversationSystemPrompt(newPrompt)
                },
            )
        }

        if (showFilesSheet) {
            ChatFilesPickerSheet(
                inputState = inputState,
                conversation = conversation,
                assistant = assistant,
                chatModel = currentChatModel,
                vm = vm,
                attachmentPickerActions = attachmentPickerActions,
                onDismiss = { showFilesSheet = false },
            )
        }
    }
    val slashPrompt = vm.pendingSlashInput
    if (slashPrompt != null) {
        SlashInputDialog(
            prompt = slashPrompt,
            onSubmit = vm::submitSlashInput,
            onDismiss = vm::dismissSlashInput,
        )
    }
    if (showBindQuickReplies) {
        QuickReplyBindDialog(
            quickMessages = setting.quickMessages,
            selected = conversation.config?.quickReplyIds.orEmpty(),
            onDismiss = { showBindQuickReplies = false },
            onSave = { ids ->
                vm.updateQuickReplyIds(ids)
                showBindQuickReplies = false
            },
        )
    }
    }
}

@Composable
private fun SlashInputDialog(
    prompt: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(prompt) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_page_slash_input_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (prompt.isNotBlank()) Text(prompt)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(text) }) {
                Text(stringResource(R.string.chat_page_save))
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
private fun QuickReplyBindDialog(
    quickMessages: List<QuickMessage>,
    selected: Set<kotlin.uuid.Uuid>,
    onDismiss: () -> Unit,
    onSave: (Set<kotlin.uuid.Uuid>) -> Unit,
) {
    var picked by remember(selected) { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_page_bind_quick_replies)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (quickMessages.isEmpty()) {
                    Text(stringResource(R.string.quick_messages_page_empty_title))
                }
                quickMessages.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = item.id in picked,
                            onCheckedChange = { checked ->
                                picked = if (checked) picked + item.id else picked - item.id
                            },
                        )
                        val title = item.title.ifBlank { item.content }
                        Text(if (item.setName.isBlank()) title else "${item.setName} · $title")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(picked) }) {
                Text(stringResource(R.string.chat_page_save))
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
private fun ChatFilesPickerSheet(
    inputState: ChatInputState,
    conversation: Conversation,
    assistant: Assistant,
    chatModel: Model?,
    vm: ChatVM,
    attachmentPickerActions: ChatAttachmentPickerActions,
    onDismiss: () -> Unit,
) {
    var showCompressDialog by remember { mutableStateOf(false) }

    fun dismissAll() {
        showCompressDialog = false
        onDismiss()
    }

    val filesSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(
        sheetState = filesSheetState,
        onDismissRequest = { dismissAll() },
    ) {
        FilesPicker(
            conversation = conversation,
            state = inputState,
            assistant = assistant,
            chatModel = chatModel,
            mcpManager = vm.mcpManager,
            onCompressContext = vm::handleCompressContext,
            onSummarizeNow = vm::summarizeNow,
            onUpdateAssistant = vm::updateAssistant,
            showCompressDialog = showCompressDialog,
            onShowCompressDialogChange = { showCompressDialog = it },
            onDismiss = { dismissAll() },
            onTakePic = attachmentPickerActions.onTakePicture,
            onPickImage = attachmentPickerActions.onPickImage,
            onPickVideo = attachmentPickerActions.onPickVideo,
            onPickAudio = attachmentPickerActions.onPickAudio,
            onSketch = attachmentPickerActions.onSketch,
        )
    }
}

@Composable
private fun TopBar(
    settings: Settings,
    assistant: Assistant,
    chatModel: Model?,
    conversation: Conversation,
    drawerState: DrawerState,
    bigScreen: Boolean,
    previewMode: Boolean,
    onClickMenu: () -> Unit,
    onNewChat: () -> Unit,
    onUpdateTitle: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val titleState = useEditState<String> {
        onUpdateTitle(it)
    }
    var showPrefixDiff by rememberSaveable { mutableStateOf(false) }
    val promptTokens = conversation.currentMessages.sumOf { it.usage?.promptTokens ?: 0 }
    val cachedTokens = conversation.currentMessages.sumOf { it.usage?.cachedTokens ?: 0 }
    val cachePercent = if (promptTokens > 0) ((cachedTokens.toLong() * 100) / promptTokens).toInt() else 0
    val hasPrefix = conversation.promptState.lastFullRequest.isNotEmpty()

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        navigationIcon = {
            if (!bigScreen) {
                IconButton(
                    onClick = {
                        scope.launch { drawerState.open() }
                    }
                ) {
                    Icon(HugeIcons.Menu03, "Messages")
                }
            }
        },
        title = {
            val editTitleWarning = stringResource(R.string.chat_page_edit_title_warning)
            Surface(
                onClick = {
                    if (conversation.messageNodes.isNotEmpty()) {
                        titleState.open(conversation.title)
                    } else {
                        toaster.show(editTitleWarning, type = ToastType.Warning)
                    }
                },
                color = Color.Transparent,
            ) {
                Column {
                    val provider = chatModel?.findProvider(providers = settings.providers, checkOverwrite = false)
                    Text(
                        text = conversation.title.ifBlank { stringResource(R.string.chat_page_new_chat) },
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (chatModel != null && provider != null) {
                        Text(
                            text = "${assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) }} / ${chatModel.displayName} (${provider.name})",
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            color = LocalContentColor.current.copy(0.65f),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                            )
                        )
                    }
                    if (promptTokens > 0 || hasPrefix) {
                        Text(
                            text = if (promptTokens > 0) {
                                stringResource(R.string.chat_page_cache_hit_rate, cachePercent)
                            } else {
                                stringResource(R.string.chat_page_prefix_diff)
                            },
                            modifier = Modifier.clickable { showPrefixDiff = true },
                            maxLines = 1,
                            color = LocalContentColor.current.copy(0.65f),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(
                onClick = {
                    onClickMenu()
                }
            ) {
                Icon(if (previewMode) HugeIcons.Cancel01 else HugeIcons.LeftToRightListBullet, "Chat Options")
            }

            IconButton(
                onClick = {
                    onNewChat()
                }
            ) {
                Icon(HugeIcons.MessageAdd01, "New Message")
            }
        },
    )
    titleState.EditStateContent { title, onUpdate ->
        AlertDialog(
            onDismissRequest = {
                titleState.dismiss()
            },
            title = {
                Text(stringResource(R.string.chat_page_edit_title))
            },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = onUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        titleState.confirm()
                    }
                ) {
                    Text(stringResource(R.string.chat_page_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        titleState.dismiss()
                    }
                ) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }
    if (showPrefixDiff) {
        val state = conversation.promptState
        AlertDialog(
            onDismissRequest = { showPrefixDiff = false },
            title = { Text(stringResource(R.string.chat_page_prefix_diff_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        if (state.prefixDivergeAt < 0) {
                            stringResource(R.string.chat_page_prefix_diff_stable)
                        } else {
                            stringResource(R.string.chat_page_prefix_diff_at, state.prefixDivergeAt)
                        }
                    )
                    if (state.prefixDivergeAt >= 0) {
                        Text(stringResource(R.string.chat_page_prefix_diff_previous))
                        Text(state.divergePrevious.take(500))
                        Text(stringResource(R.string.chat_page_prefix_diff_current))
                        Text(state.divergeCurrent.take(500))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrefixDiff = false }) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            },
        )
    }
}

@Composable
private fun SummaryReviewDialog(
    pending: PendingSummaryReview,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember(pending.draft, pending.startFloor, pending.endFloor) { mutableStateOf(pending.draft) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(stringResource(R.string.chat_page_summary_review_title, pending.startFloor, pending.endFloor))
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.chat_page_summary_commit))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.chat_page_cancel))
            }
        },
    )
}
