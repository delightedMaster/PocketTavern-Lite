package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.assembly.ANCHOR_START
import me.rerere.rikkahub.data.ai.assembly.AssemblyRequest
import me.rerere.rikkahub.data.ai.assembly.HistoryMessage
import me.rerere.rikkahub.data.ai.assembly.LoreTrigger
import me.rerere.rikkahub.data.ai.assembly.PromptCacheState
import me.rerere.rikkahub.data.ai.assembly.PromptLayer
import me.rerere.rikkahub.data.ai.assembly.PromptPiece
import me.rerere.rikkahub.data.ai.assembly.assemblePrompt
import me.rerere.rikkahub.data.ai.assembly.cleanupPromptState
import me.rerere.rikkahub.data.ai.assembly.firstDivergence
import me.rerere.rikkahub.data.ai.group.GROUP_PREAMBLE
import me.rerere.rikkahub.data.ai.group.speakerCue
import me.rerere.rikkahub.data.ai.prompts.buildMemoryPrompt
import me.rerere.rikkahub.data.ai.transformers.collectInjections
import me.rerere.rikkahub.data.datastore.authorNoteEntriesOrLegacy
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.assembleCharacterCardMessages
import me.rerere.rikkahub.data.model.assembleMainPrompt
import me.rerere.rikkahub.data.model.buildExampleMessages
import me.rerere.rikkahub.data.model.replaceRegexes
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.uuid.Uuid

private val STATIC_POSITIONS = setOf(
    InjectionPosition.BEFORE_SYSTEM_PROMPT,
    InjectionPosition.AFTER_SYSTEM_PROMPT,
    InjectionPosition.BEFORE_CHARACTER,
    InjectionPosition.AFTER_CHARACTER,
    InjectionPosition.ANTAGONIZE,
    InjectionPosition.EM_TOP,
    InjectionPosition.EM_BOTTOM,
    InjectionPosition.TOP_OF_CHAT,
)

internal data class LayeredPrompt(
    val messages: List<UIMessage>,
    val state: PromptCacheState,
)

/**
 * Tools, system, character card and constant lore stay in front.
 * Summary and history only grow. This turn's lore, author's note, time,
 * memory and tool text sit in the tail, ahead of the newest user message.
 */
internal fun ChatTurnContext.layeredPrompt(
    messages: List<UIMessage>,
): LayeredPrompt {
    val assistant = this.assistant
    val userName = settings.displaySetting.userNickname.ifBlank { "User" }
    val promptMessages = messages.mapIndexed { index, message ->
        message.withPromptRegex(assistant, settings.globalRegexes, messages.size - index)
    }
    val turnId = promptMessages.lastOrNull { it.role == MessageRole.USER }?.id?.toString().orEmpty()
    val (constant, triggered) = if (turnId.isNotEmpty() && promptState.frozenTurnId == turnId) {
        promptState.frozenConstant to promptState.frozenTriggered
    } else {
        resolveInjections(promptMessages)
    }
    val state = promptState
    val static = staticLayers(assistant, userName, constant)
    val history = promptMessages
        .filter { !it.archived && it.id.toString() !in state.summarizedIds }
        .map { message ->
            val frozen = state.frozenText[message.id.toString()]
            HistoryMessage(
                id = message.id.toString(),
                role = when (message.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                    else -> "system"
                },
                text = frozen ?: message.toText(),
                checkpoint = message.isContextCheckpoint,
            )
        }
    val output = assemblePrompt(
        AssemblyRequest(
            staticLayers = static,
            history = history,
            triggeredLore = triggered,
            authorNote = authorNoteText(promptMessages.count { it.role == MessageRole.USER }),
            timeText = tailTime(),
            memoryText = memoryText(),
            toolText = toolText(messages),
            stateVars = stateVarText(promptMessages.lastOrNull { !it.archived }?.id?.toString()),
            speakerText = speakerName?.let { speakerCue(it) },
            state = state.copy(
                frozenTurnId = turnId,
                frozenConstant = constant,
                frozenTriggered = triggered,
            ),
            storeRenderedText = false,
        ),
    )
    val liveIds = buildSet {
        addAll(settings.lorebooks.flatMap { book -> book.entries.map { it.id.toString() } })
        addAll(settings.modeInjections.map { it.id.toString() })
        assistant.tavernData?.embeddedBook?.entries?.forEach { add("embedded:${it.id}") }
        addAll(output.state.anchors.map { it.entryId })
    }
    val cleaned = cleanupPromptState(
        output.state,
        liveEntryIds = liveIds,
        liveMessageIds = history.map { it.id }.toSet() + ANCHOR_START,
    )
    return LayeredPrompt(output.pieces.map { it.toUiMessage() }, cleaned)
}

internal fun PromptCacheState.withFinalRequest(texts: List<String>): PromptCacheState {
    val diverge = firstDivergence(lastFullRequest, texts)
    return copy(
        lastFullRequest = texts,
        prefixDivergeAt = diverge,
        divergePrevious = if (diverge >= 0) lastFullRequest.getOrElse(diverge) { "" } else "",
        divergeCurrent = if (diverge >= 0) texts.getOrElse(diverge) { "" } else "",
    )
}

internal fun captureFrozenMacros(
    original: List<UIMessage>,
    rendered: List<UIMessage>,
    state: PromptCacheState,
): Map<String, String> {
    val byId = rendered.associateBy { it.id.toString() }
    val frozen = state.frozenText.toMutableMap()
    original.forEach { message ->
        val id = message.id.toString()
        val text = message.toText()
        val renderedText = byId[id]?.toText() ?: return@forEach
        if (id !in frozen && text.contains('{') && renderedText != text) {
            frozen[id] = renderedText
        }
    }
    return frozen
}

private fun ChatTurnContext.resolveInjections(
    messages: List<UIMessage>,
): Pair<List<String>, List<LoreTrigger>> {
    val sticky = promptState.sticky.mapNotNull { (key, turns) ->
        runCatching { Uuid.parse(key) }.getOrNull()?.let { it to turns }
    }.toMap(mutableMapOf())
    val cooldown = promptState.cooldown.mapNotNull { (key, turns) ->
        runCatching { Uuid.parse(key) }.getOrNull()?.let { it to turns }
    }.toMap(mutableMapOf())
    val injections = collectInjections(
        messages = messages.filter { !it.isSynthetic },
        assistant = assistant,
        modeInjections = settings.modeInjections,
        lorebooks = settings.lorebooks,
        conversationModeInjectionIds = conversationModeInjectionIds,
        conversationLorebookIds = conversationLorebookIds,
        activeStickyEntries = sticky,
        cooldownEntries = cooldown,
        worldInfoBudget = settings.worldInfoBudget,
        worldInfoBudgetCap = settings.worldInfoBudgetCap,
        worldInfoMinActivations = settings.worldInfoMinActivations,
        worldInfoMinActivationsDepthMax = settings.worldInfoMinActivationsDepthMax,
        worldInfoRecursive = settings.worldInfoRecursive,
        worldInfoMaxRecursionSteps = settings.worldInfoMaxRecursionSteps,
        worldInfoDepth = settings.worldInfoDepth,
        worldInfoCharacterStrategy = settings.worldInfoCharacterStrategy,
        worldInfoOverflowAlert = settings.worldInfoOverflowAlert,
        worldInfoUseGroupScoring = settings.worldInfoUseGroupScoring,
        personaDescription = settings.personas
            .firstOrNull { it.id == settings.activePersonaId && it.enabled }
            ?.description.orEmpty(),
    )
    promptState = promptState.copy(
        sticky = sticky.mapKeys { it.key.toString() },
        cooldown = cooldown.mapKeys { it.key.toString() },
    )
    val constant = mutableListOf<PromptInjection>()
    val triggered = mutableListOf<PromptInjection>()
    injections.forEach { injection ->
        val alwaysOn = injection is PromptInjection.ModeInjection ||
            (injection is PromptInjection.RegexInjection && injection.constantActive)
        if (alwaysOn && injection.position in STATIC_POSITIONS) constant += injection
        else triggered += injection
    }
    val constantText = constant
        .sortedWith(compareBy({ it.position.ordinal }, { -it.priority }, { it.id.toString() }))
        .map { it.content }
    val triggers = triggered
        .sortedWith(compareBy({ it.position.ordinal }, { -it.priority }, { it.id.toString() }))
        .map { LoreTrigger(it.id.toString(), it.content, it.injectDepth) }
    return constantText to triggers
}

private fun ChatTurnContext.staticLayers(
    assistant: Assistant,
    userName: String,
    constant: List<String>,
): List<String> {
    val tavern = assistant.tavernData != null &&
        !(assistant.allowConversationSystemPrompt && !conversationSystemPrompt.isNullOrBlank())
    val system = when {
        assistant.allowConversationSystemPrompt && !conversationSystemPrompt.isNullOrBlank() ->
            conversationSystemPrompt
        tavern -> assistant.assembleMainPrompt().ifBlank {
            "Write ${assistant.name.ifBlank { "the character" }}'s next reply in a fictional chat between ${assistant.name.ifBlank { "the character" }} and $userName."
        }
        else -> assistant.systemPrompt
    }
    return buildList {
        if (groupCards.isNotEmpty()) {
            add(GROUP_PREAMBLE)
            addAll(groupCards)
        } else {
            if (system.isNotBlank()) add(system)
            if (tavern) {
                addAll(assistant.assembleCharacterCardMessages().map { it.toText() })
                addAll(assistant.buildExampleMessages(userName).map { it.toText() })
            }
        }
        addAll(constant)
    }
}

private fun ChatTurnContext.authorNoteText(userTurns: Int): String? {
    if (!settings.authorNoteEnabled) return null
    val notes = settings.authorNoteEntriesOrLegacy().filter { entry ->
        entry.enabled && entry.content.isNotBlank() &&
            (entry.interval <= 1 || userTurns % entry.interval == 0)
    }.map { it.content }
    val persona = settings.personas.firstOrNull { it.id == settings.activePersonaId && it.enabled }
        ?.description?.takeIf { it.isNotBlank() }
    val lines = notes + listOfNotNull(persona)
    if (lines.isEmpty()) return null
    return lines.joinToString("\n\n")
}

private fun ChatTurnContext.tailTime(): String? {
    if (!assistant.enableTimeReminder) return null
    val now = LocalDateTime.now()
    val stamp = if (assistant.cacheFriendlyTimeMacros) {
        now.minusMinutes((now.minute % 5).toLong()).withSecond(0).withNano(0)
    } else {
        now.withNano(0)
    }
    return "<time_reminder>Current time: ${stamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}</time_reminder>"
}

private fun ChatTurnContext.memoryText(): String? {
    if (!assistant.enableMemory || memories.isEmpty()) return null
    return buildMemoryPrompt(memories)
}

private fun ChatTurnContext.toolText(messages: List<UIMessage>): String? {
    val lines = tools.sortedWith(compareBy({ it.name }, { it.description }))
        .map { it.systemPrompt(model, messages) }
        .filter { it.isNotBlank() }
    if (lines.isEmpty()) return null
    return lines.joinToString("\n")
}

private fun ChatTurnContext.stateVarText(latestMessageId: String?): String? {
    val id = conversationId.toString()
    val chat = settings.macroChatVariables[id].orEmpty().toSortedMap()
    val global = settings.macroGlobalVariables.toSortedMap()
    val message = latestMessageId?.let { promptState.messageVariables[it] }.orEmpty().toSortedMap()
    if (chat.isEmpty() && global.isEmpty() && message.isEmpty()) return null
    return buildString {
        global.forEach { (key, value) -> appendLine("global.$key=$value") }
        chat.forEach { (key, value) -> appendLine("chat.$key=$value") }
        message.forEach { (key, value) -> appendLine("message.$key=$value") }
    }.trim()
}

private fun PromptPiece.toUiMessage(): UIMessage {
    val role = when (this.role) {
        "user" -> MessageRole.USER
        "assistant" -> MessageRole.ASSISTANT
        else -> MessageRole.SYSTEM
    }
    val id = messageId?.let { runCatching { Uuid.parse(it) }.getOrNull() } ?: Uuid.random()
    val annotations = cacheBreakpoint?.let { listOf(UIMessageAnnotation.CacheBreakpoint(it)) }.orEmpty()
    return UIMessage(
        id = id,
        role = role,
        parts = listOf(UIMessagePart.Text(text)),
        annotations = annotations,
        isSynthetic = layer != PromptLayer.HISTORY && layer != PromptLayer.USER,
    )
}

private fun UIMessage.withPromptRegex(
    assistant: Assistant,
    globalRegexes: List<me.rerere.rikkahub.data.model.AssistantRegex>,
    depth: Int,
): UIMessage {
    val scope = when (role) {
        MessageRole.USER -> AssistantAffectScope.USER
        MessageRole.ASSISTANT -> AssistantAffectScope.ASSISTANT
        else -> return this
    }
    val regexes = assistant.regexes + globalRegexes
    if (regexes.isEmpty()) return this
    return copy(
        parts = parts.map { part ->
            when (part) {
                is UIMessagePart.Text -> part.copy(
                    text = part.text.replaceRegexes(regexes, scope, visual = false, depth = depth),
                )
                is UIMessagePart.Reasoning -> part.copy(
                    reasoning = part.reasoning.replaceRegexes(regexes, scope, visual = false, depth = depth),
                )
                else -> part
            }
        },
    )
}

internal fun List<Tool>.stableOrder(): List<Tool> =
    sortedWith(compareBy({ it.name }, { it.description }))
