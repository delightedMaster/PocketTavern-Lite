package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.assembly.ANCHOR_START
import me.rerere.rikkahub.data.ai.assembly.cleanupPromptState
import me.rerere.rikkahub.data.ai.assembly.renderMemoryLore
import me.rerere.rikkahub.data.ai.group.GROUP_PREAMBLE
import me.rerere.rikkahub.data.datastore.authorNoteEntriesOrLegacy
import me.rerere.rikkahub.data.model.AuthorNotePosition
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.StPresetSettings
import me.rerere.rikkahub.data.model.buildExampleMessages
import me.rerere.rikkahub.data.model.isOrderedTavernPreset

/**
 * SillyTavern Chat Completion prompt assembly (PromptManager + openai.js populateChatCompletion).
 *
 * The preset's entries are already in prompt_order. Each enabled entry becomes one message
 * in place, with its own role. Markers are filled from local data:
 *
 * | marker             | content                                                     |
 * |--------------------|-------------------------------------------------------------|
 * | worldInfoBefore    | triggered lore at BEFORE_CHARACTER                          |
 * | worldInfoAfter     | triggered lore at AFTER_CHARACTER / ANTAGONIZE              |
 * | personaDescription | active persona                                              |
 * | charDescription    | card description, or the assistant's system prompt          |
 * | charPersonality    | personality_format                                          |
 * | scenario           | scenario_format                                             |
 * | dialogueExamples   | EM_TOP lore, example messages, EM_BOTTOM lore               |
 * | chatHistory        | summary, history, and every in-chat (absolute) injection    |
 *
 * Absolute entries (injection_position = 1), at-depth lore and the author's note are inserted
 * into chat history at their depth (0 = after the newest message), the way
 * populationInjectionPrompts does it. Entries after chatHistory therefore follow the whole
 * history, so a trailing assistant entry is a real prefill.
 *
 * Macros are expanded later by the input transformers, in this message order, so
 * {{setvar}} in an early entry is visible to {{getvar}} in a later one, as in SillyTavern.
 */
internal fun ChatTurnContext.activeTavernPreset(): Lorebook? =
    settings.lorebooks.firstOrNull { book ->
        book.enabled && book.isOrderedTavernPreset && book.id in assistant.lorebookIds
    }

/** One in-chat injection: inserted [depth] messages from the end of chat history. */
internal data class InChatInjection(
    val depth: Int,
    val order: Int,
    val role: MessageRole,
    val text: String,
)

internal fun ChatTurnContext.tavernPresetPrompt(
    messages: List<UIMessage>,
    preset: Lorebook,
): LayeredPrompt {
    val userName = settings.displaySetting.userNickname.ifBlank { "User" }
    val promptMessages = messages.mapIndexed { index, message ->
        message.withPromptRegex(assistant, settings.globalRegexes, messages.size - index - 1)
    }
    val turnId = promptMessages.lastOrNull { it.role == MessageRole.USER }?.id?.toString().orEmpty()
    val lore = if (turnId.isNotEmpty() && promptState.frozenInjectionsTurnId == turnId) {
        promptState.frozenInjections
    } else {
        resolveInjectionList(promptMessages, excludeLorebookId = preset.id)
    }
    val state = promptState

    val history = promptMessages
        .filter { !it.archived && it.id.toString() !in state.summarizedIds }
        .map { message ->
            val frozen = state.frozenText[message.id.toString()]
            message.copy(parts = listOf(UIMessagePart.Text(frozen ?: message.toText())))
        }

    val inChat = buildList<InChatInjection> {
        preset.entries.forEach { entry ->
            val meta = entry.stPrompt ?: return@forEach
            if (!entry.enabled || meta.marker || !meta.absolute || entry.content.isBlank()) return@forEach
            add(InChatInjection(entry.injectDepth, meta.injectionOrder, entry.role, entry.content))
        }
        lore.forEach { injection ->
            val depth = when (injection.position) {
                InjectionPosition.AT_DEPTH -> injection.injectDepth
                InjectionPosition.BOTTOM_OF_CHAT -> 1
                InjectionPosition.AFTER_DIALOG -> 0
                InjectionPosition.TOP_OF_CHAT -> Int.MAX_VALUE
                InjectionPosition.AUTHOR_NOTE -> settings.authorNoteDepth
                else -> return@forEach
            }
            if (injection.content.isNotBlank()) add(InChatInjection(depth, 100, injection.role, injection.content))
        }
        addAll(authorNoteInjections(promptMessages.count { it.role == MessageRole.USER }))
        // App-only context (variables, time, memory, tools, group speaker) goes right before
        // the newest message, where the layered prompt also puts it.
        val extras = listOfNotNull(
            stateVarText(promptMessages.lastOrNull { !it.archived }?.id?.toString()),
            tailTime(),
            memoryText(),
            toolText(messages),
            speakerName?.let { me.rerere.rikkahub.data.ai.group.speakerCue(it) },
        ).filter { it.isNotBlank() }
        if (extras.isNotEmpty()) add(InChatInjection(1, 100, MessageRole.SYSTEM, extras.joinToString("\n\n")))
    }

    fun loreAt(vararg positions: InjectionPosition): String? {
        val text = lore.filter { it.position in positions && it.content.isNotBlank() }
            .joinToString("\n") { it.content }
        if (text.isBlank()) return null
        val format = preset.stPreset?.wiFormat ?: "{0}"
        return if (format.contains("{0}")) format.replace("{0}", text) else text
    }

    val settingsOf = preset.stPreset ?: StPresetSettings()
    val tav = assistant.tavernData
    val conversationPrompt = conversationSystemPrompt
        ?.takeIf { assistant.allowConversationSystemPrompt && it.isNotBlank() }
    val out = mutableListOf<UIMessage>()
    fun synthetic(role: MessageRole, text: String?) {
        if (text.isNullOrBlank()) return
        out += UIMessage(role = role, parts = listOf(UIMessagePart.Text(text)), isSynthetic = true)
    }

    preset.entries.forEach { entry ->
        if (!entry.enabled) return@forEach
        val meta = entry.stPrompt
        if (meta == null) {
            // Added in the app after import: a relative entry at its list position.
            synthetic(entry.role, entry.content)
            return@forEach
        }
        if (meta.absolute && !meta.marker) return@forEach
        if (!meta.marker) {
            val content = when (meta.identifier) {
                // SillyTavern prefer_character_prompt / prefer_character_jailbreak: the card wins,
                // {{original}} keeps the preset text.
                "main" -> tav?.systemPrompt?.takeIf { it.isNotBlank() }
                    ?.replace("{{original}}", entry.content, ignoreCase = true) ?: entry.content
                "jailbreak" -> tav?.postHistoryInstructions?.takeIf { it.isNotBlank() }
                    ?.replace("{{original}}", entry.content, ignoreCase = true) ?: entry.content
                else -> entry.content
            }
            synthetic(entry.role, content)
            return@forEach
        }
        when (meta.identifier) {
            "worldInfoBefore" -> synthetic(MessageRole.SYSTEM, loreAt(InjectionPosition.BEFORE_CHARACTER))
            "worldInfoAfter" -> synthetic(
                MessageRole.SYSTEM,
                loreAt(InjectionPosition.AFTER_CHARACTER, InjectionPosition.ANTAGONIZE),
            )

            "personaDescription" -> synthetic(
                MessageRole.SYSTEM,
                settings.personas.firstOrNull { it.id == settings.activePersonaId && it.enabled }?.description,
            )

            "charDescription" -> when {
                groupCards.isNotEmpty() -> {
                    synthetic(MessageRole.SYSTEM, GROUP_PREAMBLE)
                    groupCards.forEach { synthetic(MessageRole.SYSTEM, it) }
                }

                conversationPrompt != null -> synthetic(MessageRole.SYSTEM, conversationPrompt)
                tav != null -> synthetic(MessageRole.SYSTEM, tav.description)
                else -> synthetic(MessageRole.SYSTEM, assistant.systemPrompt)
            }

            "charPersonality" -> tav?.personality?.takeIf { it.isNotBlank() && groupCards.isEmpty() }?.let {
                synthetic(MessageRole.SYSTEM, settingsOf.personalityFormat.replace("{{personality}}", it, ignoreCase = true))
            }

            "scenario" -> tav?.scenario?.takeIf { it.isNotBlank() && groupCards.isEmpty() }?.let {
                synthetic(MessageRole.SYSTEM, settingsOf.scenarioFormat.replace("{{scenario}}", it, ignoreCase = true))
            }

            "dialogueExamples" -> {
                synthetic(MessageRole.SYSTEM, loreAt(InjectionPosition.EM_TOP))
                if (groupCards.isEmpty()) {
                    out += assistant.buildExampleMessages(userName).map { it.copy(isSynthetic = true) }
                }
                synthetic(MessageRole.SYSTEM, loreAt(InjectionPosition.EM_BOTTOM))
            }

            "chatHistory" -> {
                synthetic(MessageRole.SYSTEM, renderMemoryLore(state.memoryBlocks))
                synthetic(MessageRole.SYSTEM, loreAt(InjectionPosition.BEFORE_SYSTEM_PROMPT, InjectionPosition.AFTER_SYSTEM_PROMPT))
                out += insertInChat(history, inChat)
            }

            else -> Unit // enhanceDefinitions etc. carry content and are handled above
        }
    }

    val ordered = if (settingsOf.squashSystemMessages) squashSystemMessages(out) else out
    promptState = state.copy(
        frozenInjectionsTurnId = turnId,
        frozenInjections = lore,
    )
    val liveIds = buildSet {
        addAll(settings.lorebooks.flatMap { book -> book.entries.map { it.id.toString() } })
        addAll(settings.modeInjections.map { it.id.toString() })
    }
    val cleaned = cleanupPromptState(
        promptState,
        liveEntryIds = liveIds,
        liveMessageIds = history.map { it.id.toString() }.toSet() + ANCHOR_START,
    )
    return LayeredPrompt(ordered, cleaned)
}

/**
 * populationInjectionPrompts: every depth block sits [InChatInjection.depth] messages from the end.
 * Within a depth, lower injection_order comes first; within an order, assistant, user, system
 * (system ends up closest to the newest message). Same-role texts join with a newline.
 */
internal fun insertInChat(history: List<UIMessage>, injections: List<InChatInjection>): List<UIMessage> {
    if (injections.isEmpty()) return history
    val roleRank = mapOf(MessageRole.ASSISTANT to 0, MessageRole.USER to 1, MessageRole.SYSTEM to 2)
    val roleOrder = compareBy<MessageRole>({ roleRank[it] ?: 3 }, { it.name })
    val byDepth = injections.groupBy { it.depth.coerceIn(0, history.size) }
    val result = history.toMutableList()
    // Shallowest first: it sits at the highest index, so inserting it never shifts a deeper block's index.
    byDepth.keys.sorted().forEach { depth ->
        val block = byDepth.getValue(depth)
            .groupBy { it.order }
            .toSortedMap()
            .values
            .flatMap { sameOrder ->
                sameOrder.groupBy { it.role }
                    .toSortedMap(roleOrder)
                    .map { (role, items) ->
                        UIMessage(
                            role = role,
                            parts = listOf(UIMessagePart.Text(items.joinToString("\n") { it.text.trim() })),
                            isSynthetic = true,
                        )
                    }
            }
        result.addAll(history.size - depth, block)
    }
    return result
}

/** squash_system_messages: merge runs of synthetic system messages, joined by a newline. */
internal fun squashSystemMessages(messages: List<UIMessage>): List<UIMessage> {
    val result = mutableListOf<UIMessage>()
    messages.forEach { message ->
        val text = message.toText()
        if (message.role == MessageRole.SYSTEM && message.isSynthetic && text.isBlank()) return@forEach
        val last = result.lastOrNull()
        if (message.role == MessageRole.SYSTEM && message.isSynthetic &&
            last != null && last.role == MessageRole.SYSTEM && last.isSynthetic
        ) {
            result[result.lastIndex] = last.copy(parts = listOf(UIMessagePart.Text(last.toText() + "\n" + text)))
        } else {
            result += message
        }
    }
    return result
}

private fun ChatTurnContext.authorNoteInjections(userTurns: Int): List<InChatInjection> {
    if (!settings.authorNoteEnabled) return emptyList()
    return settings.authorNoteEntriesOrLegacy()
        .filter { it.enabled && it.content.isNotBlank() && (it.interval <= 1 || userTurns % it.interval == 0) }
        .map { note ->
            val depth = when (note.position) {
                AuthorNotePosition.IN_CHAT -> note.depth
                // Before / in the main prompt has no slot in a preset that does not name it:
                // keep it at the top of chat history, which is the closest SillyTavern place.
                AuthorNotePosition.IN_PROMPT, AuthorNotePosition.BEFORE_PROMPT -> Int.MAX_VALUE
            }
            InChatInjection(depth, 100, note.role, note.content)
        }
}
