package me.rerere.rikkahub.data.ai.assembly

/**
 * Decides which floors leave the live prompt and how their summary is stored.
 *
 * [FloorSummaryChunker] appends one block to the chat-scoped memory lorebook and
 * records the ids to archive. Hiding, deleting, and the manual action live in
 * the chat service, which calls the same plan and commit functions.
 */
interface ContextChunker {
    fun plan(history: List<HistoryMessage>, policy: FloorPolicy, archivedIds: Set<String>): ChunkPlan

    fun commit(
        state: PromptCacheState,
        history: List<HistoryMessage>,
        plan: ChunkPlan,
        summary: String?,
    ): PromptCacheState
}

object FloorSummaryChunker : ContextChunker {
    override fun plan(history: List<HistoryMessage>, policy: FloorPolicy, archivedIds: Set<String>): ChunkPlan =
        planFloorSummary(history, policy, archivedIds)

    override fun commit(
        state: PromptCacheState,
        history: List<HistoryMessage>,
        plan: ChunkPlan,
        summary: String?,
    ): PromptCacheState = commitFloorSummary(state, history, plan, summary)
}

internal fun liveHistory(history: List<HistoryMessage>, archivedIds: Set<String>): List<HistoryMessage> {
    val checkpoint = history.indexOfLast { it.checkpoint }
    val afterCheckpoint = if (checkpoint >= 0) history.drop(checkpoint + 1) else history
    return afterCheckpoint.filter { it.id !in archivedIds }
}
