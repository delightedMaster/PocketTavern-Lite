package me.rerere.rikkahub.data.ai.assembly

/**
 * When the live floor count reaches [triggerFloors], summarize every floor except
 * the most recent [keepFloors]. [force] is the manual "summarize now" path.
 */
data class FloorPolicy(
    val triggerFloors: Int = 40,
    val keepFloors: Int = 10,
    val force: Boolean = false,
)

/** Rough token count. The consolidation threshold uses the same estimator. */
fun estimateTokens(text: String): Int = text.length / 4

/**
 * Renders the chat-scoped memory lorebook. Each block is append-only except a
 * recap, which replaces older blocks as a new version.
 * Separator shape: `---\n[start-end]`.
 */
fun renderMemoryLore(blocks: List<MemoryBlock>): String = blocks.joinToString("\n") { block ->
    val header = if (block.kind == "recap") {
        "[recap v${block.version} | ${block.startFloor}-${block.endFloor}]"
    } else {
        "[${block.startFloor}-${block.endFloor}]"
    }
    "---\n$header\n${block.text}"
}

fun planFloorSummary(
    history: List<HistoryMessage>,
    policy: FloorPolicy,
    archivedIds: Set<String>,
): ChunkPlan {
    val live = liveHistory(history, archivedIds)
    val keep = policy.keepFloors.coerceAtLeast(0)
    val trigger = policy.triggerFloors.coerceAtLeast(keep + 1)
    val due = if (policy.force) live.size > keep else live.size >= trigger
    if (!due) return ChunkPlan(emptyList(), emptyList())
    val drop = live.size - keep
    if (drop <= 0) return ChunkPlan(emptyList(), emptyList())
    val chunk = live.take(drop)
    return ChunkPlan(chunk.map { it.id }, chunk.map { it.text })
}

/**
 * Appends one summary block and records the archived ids.
 * A blank summary commits nothing, so a failed model call does not hide messages.
 */
fun commitFloorSummary(
    state: PromptCacheState,
    history: List<HistoryMessage>,
    plan: ChunkPlan,
    summary: String?,
): PromptCacheState {
    if (plan.dropIds.isEmpty() || summary.isNullOrBlank()) return state
    val floors = history.mapIndexed { index, message -> message.id to (index + 1) }.toMap()
    val start = floors[plan.dropIds.first()] ?: 1
    val end = floors[plan.dropIds.last()] ?: start
    val block = MemoryBlock(
        kind = "summary",
        startFloor = start,
        endFloor = end,
        text = summary.trim(),
    )
    val dropped = plan.dropIds.toSet()
    return state.copy(
        memoryBlocks = state.memoryBlocks + block,
        summarizedIds = state.summarizedIds + dropped,
        anchors = state.anchors.filter { it.afterMessageId !in dropped },
        frozenText = state.frozenText.filterKeys { it !in dropped },
    )
}

fun memoryOverThreshold(blocks: List<MemoryBlock>, tokenThreshold: Int): Boolean {
    if (tokenThreshold <= 0 || blocks.isEmpty()) return false
    return estimateTokens(renderMemoryLore(blocks)) > tokenThreshold
}

/**
 * Oldest blocks become one recap. The newest summary text is copied through unchanged.
 * Returns null when there is nothing older than the newest summary.
 */
fun consolidateMemory(blocks: List<MemoryBlock>, recapText: String): List<MemoryBlock>? {
    if (recapText.isBlank()) return null
    val lastSummary = blocks.indexOfLast { it.kind == "summary" }
    if (lastSummary <= 0) return null
    val older = blocks.subList(0, lastSummary)
    val version = (older.maxOfOrNull { if (it.kind == "recap") it.version else 0 } ?: 0) + 1
    val recap = MemoryBlock(
        kind = "recap",
        startFloor = older.first().startFloor,
        endFloor = older.last().endFloor,
        version = version,
        text = recapText.trim(),
    )
    return listOf(recap) + blocks.subList(lastSummary, blocks.size)
}

fun formatPassage(messages: List<HistoryMessage>): String =
    messages.joinToString("\n") { message -> "${message.role}: ${message.text}" }

fun fillSummaryPrompt(template: String, playerName: String, prior: String, passage: String): String =
    template
        .replace("{{player_name}}", playerName.ifBlank { "User" })
        .replace("{{context_str}}", prior.ifBlank { "(none yet)" })
        .replace("{{story_txt}}", passage)
