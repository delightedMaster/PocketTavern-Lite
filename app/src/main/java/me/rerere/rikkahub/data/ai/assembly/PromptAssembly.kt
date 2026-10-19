package me.rerere.rikkahub.data.ai.assembly

import kotlinx.serialization.Serializable
import kotlin.math.ceil

/** Where a depth injection sits. Anchored and tail keep the history prefix stable. */
@Serializable
enum class DepthInjectionMode {
    /** Insert once, after a fixed message, and leave it there. */
    ANCHORED,

    /** Keep this turn's triggers in the dynamic tail. */
    TAIL,

    /** SillyTavern relative depth. The insertion point moves as the chat grows. */
    RELATIVE,
}

fun parseDepthInjectionMode(value: String?): DepthInjectionMode = when (value) {
    "TAIL" -> DepthInjectionMode.TAIL
    "RELATIVE" -> DepthInjectionMode.RELATIVE
    else -> DepthInjectionMode.ANCHORED
}

enum class PromptLayer {
    STATIC,
    SUMMARY,
    HISTORY,
    TAIL,
    USER,
}

/**
 * One block of the chat-scoped memory lorebook.
 * Summaries are append-only. A recap replaces older blocks as a new version.
 */
@Serializable
data class MemoryBlock(
    val kind: String = "summary",
    val startFloor: Int,
    val endFloor: Int,
    val version: Int = 1,
    val text: String,
)

@Serializable
data class AnchoredLore(
    val entryId: String,
    val afterMessageId: String,
    val text: String,
)

/**
 * Per-conversation cache state. Sticky/cooldown, summaries, anchors and frozen
 * macro text survive process death. [lastFullRequest] is the previous prompt,
 * used only for the prefix-diff view.
 */
@Serializable
data class PromptCacheState(
    val sticky: Map<String, Int> = emptyMap(),
    val cooldown: Map<String, Int> = emptyMap(),
    val memoryBlocks: List<MemoryBlock> = emptyList(),
    val summarizedIds: Set<String> = emptySet(),
    val anchors: List<AnchoredLore> = emptyList(),
    val frozenText: Map<String, String> = emptyMap(),
    val frozenTurnId: String = "",
    val frozenConstant: List<String> = emptyList(),
    val frozenTriggered: List<LoreTrigger> = emptyList(),
    val lastFullRequest: List<String> = emptyList(),
    val prefixDivergeAt: Int = -1,
    val divergePrevious: String = "",
    val divergeCurrent: String = "",
    /** Per-message variables written by HTML cards. The assembler copies only the latest into the tail. */
    val messageVariables: Map<String, Map<String, String>> = emptyMap(),
)

data class HistoryMessage(
    val id: String,
    val role: String,
    val text: String,
    val checkpoint: Boolean = false,
)

@Serializable
data class LoreTrigger(
    val id: String,
    val text: String,
    val depth: Int = 4,
)

data class PromptPiece(
    val layer: PromptLayer,
    val text: String,
    val messageId: String? = null,
    val role: String = "system",
    val cacheBreakpoint: String? = null,
)

data class AssemblyRequest(
    val staticLayers: List<String> = emptyList(),
    val history: List<HistoryMessage> = emptyList(),
    val triggeredLore: List<LoreTrigger> = emptyList(),
    val authorNote: String? = null,
    val timeText: String? = null,
    val memoryText: String? = null,
    val toolText: String? = null,
    val stateVars: String? = null,
    /** Who speaks this turn. Kept beside the other tail lines so member cards can stay put. */
    val speakerText: String? = null,
    /** Live history messages that trigger a trim. 0 disables trimming. */
    val contextLimit: Int = 0,
    val depthMode: DepthInjectionMode = DepthInjectionMode.ANCHORED,
    val state: PromptCacheState = PromptCacheState(),
    /**
     * Produces summary text for a chunk. Null means summarization failed.
     * Where that text is stored is [chunker]'s job.
     */
    val summarizer: (List<HistoryMessage>) -> String? = { null },
    val chunker: ContextChunker = FloorSummaryChunker,
    /** Render a history message the first time it is sent. Later turns reuse the frozen text. */
    val render: (HistoryMessage) -> String = { it.text },
    /** The app freezes text after macro expansion, so the assembler only keeps what it rendered in tests. */
    val storeRenderedText: Boolean = true,
)

data class AssemblyOutput(
    val pieces: List<PromptPiece>,
    val state: PromptCacheState,
    val trimmed: Boolean,
    val stablePrefix: List<String>,
    val fullText: List<String>,
)

data class ChunkPlan(
    val dropIds: List<String>,
    val texts: List<String>,
)

/** Oldest ~30% of [size], at least one message, always leaving at least one. */
fun chunkDropCount(size: Int): Int {
    if (size <= 1) return 0
    return ceil(size * CHUNK_DROP_RATIO).toInt().coerceIn(1, size - 1)
}

const val CHUNK_DROP_RATIO = 0.30
const val ANCHOR_START = "start"

fun sharedPrefixRatio(previous: List<String>, next: List<String>): Double {
    if (next.isEmpty()) return 1.0
    val sharedMessages = previous.zip(next).takeWhile { (a, b) -> a == b }.size
    val sharedChars = next.take(sharedMessages).sumOf { it.length }
    val total = next.sumOf { it.length }.coerceAtLeast(1)
    return sharedChars.toDouble() / total
}

fun firstDivergence(previous: List<String>, next: List<String>): Int {
    val n = minOf(previous.size, next.size)
    for (i in 0 until n) {
        if (previous[i] != next[i]) return i
    }
    return if (previous.size == next.size) -1 else n
}

/** Messages that should leave the live window. Does not mutate [state]. */
fun planChunkTrim(
    history: List<HistoryMessage>,
    policy: FloorPolicy,
    state: PromptCacheState,
    chunker: ContextChunker = FloorSummaryChunker,
): ChunkPlan = chunker.plan(history, policy, state.summarizedIds)

fun applyChunkPlan(
    state: PromptCacheState,
    history: List<HistoryMessage>,
    plan: ChunkPlan,
    summary: String?,
): PromptCacheState = commitFloorSummary(state, history, plan, summary)

/**
 * Drop sticky and cooldown rows whose turn count has run out, and anchors whose message is gone.
 * Entry ids are not filtered here: a character-book id is not in the settings lorebook list.
 */
fun cleanupPromptState(
    state: PromptCacheState,
    @Suppress("UNUSED_PARAMETER") liveEntryIds: Set<String>,
    liveMessageIds: Set<String>,
): PromptCacheState {
    val messages = liveMessageIds + ANCHOR_START
    return state.copy(
        sticky = state.sticky.filterValues { it > 0 },
        cooldown = state.cooldown.filterValues { it > 0 },
        anchors = state.anchors.filter { it.afterMessageId in messages },
    )
}

fun assemblePrompt(request: AssemblyRequest): AssemblyOutput {
    val state = request.state
    val frozen = state.frozenText.toMutableMap()
    fun rendered(message: HistoryMessage): String {
        frozen[message.id]?.let { return it }
        val text = request.render(message)
        if (request.storeRenderedText) frozen[message.id] = text
        return text
    }

    val (user, body) = peelLatestUser(liveHistory(request.history, state.summarizedIds))
    val pieces = mutableListOf<PromptPiece>()

    request.staticLayers.forEach { text ->
        if (text.isBlank()) return@forEach
        pieces += PromptPiece(PromptLayer.STATIC, text)
    }
    val memory = renderMemoryLore(state.memoryBlocks)
    if (memory.isNotBlank()) {
        pieces += PromptPiece(PromptLayer.SUMMARY, memory, cacheBreakpoint = "memory")
    }

    body.forEach { message ->
        pieces += PromptPiece(PromptLayer.HISTORY, rendered(message), message.id, role = message.role)
    }
    val lastHistory = pieces.indexOfLast { it.layer == PromptLayer.HISTORY }
    if (lastHistory >= 0) {
        pieces[lastHistory] = pieces[lastHistory].copy(cacheBreakpoint = "history")
    }

    fun tail(text: String?) {
        if (!text.isNullOrBlank()) pieces += PromptPiece(PromptLayer.TAIL, text, role = "user")
    }
    // Keyword and vector hits stay in the tail, after visible history.
    request.triggeredLore.forEach { tail(it.text) }
    tail(request.stateVars)
    tail(request.authorNote)
    tail(request.timeText)
    tail(request.memoryText)
    tail(request.toolText)
    tail(request.speakerText)
    user?.let { pieces += PromptPiece(PromptLayer.USER, rendered(it), it.id, role = "user") }

    val full = pieces.map { it.text }
    val stable = pieces.filter { it.layer != PromptLayer.TAIL && it.layer != PromptLayer.USER }.map { it.text }
    val diverge = firstDivergence(state.lastFullRequest, full)
    val newState = state.copy(
        frozenText = frozen,
        lastFullRequest = full,
        prefixDivergeAt = diverge,
        divergePrevious = if (diverge >= 0) state.lastFullRequest.getOrElse(diverge) { "" } else "",
        divergeCurrent = if (diverge >= 0) full.getOrElse(diverge) { "" } else "",
    )
    return AssemblyOutput(pieces, newState, trimmed = false, stable, full)
}

private fun peelLatestUser(history: List<HistoryMessage>): Pair<HistoryMessage?, List<HistoryMessage>> {
    val last = history.lastOrNull() ?: return null to emptyList()
    if (last.role != "user") return null to history
    return last to history.dropLast(1)
}
