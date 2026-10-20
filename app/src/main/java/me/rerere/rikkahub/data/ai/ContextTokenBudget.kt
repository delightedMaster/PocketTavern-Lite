package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.transformers.estimateTokens

/**
 * Estimate the full request after prompt expansion. Trim only complete old turns, keeping
 * synthetic prompts, the latest user turn and its tool chain. Stored history is never deleted.
 */
internal fun fitContextTokenBudget(
    messages: List<UIMessage>,
    contextTokens: Int?,
    replyTokens: Int?,
    toolTokens: Int = 0,
    allowHistoryTrim: Boolean = true,
): List<UIMessage> {
    if (contextTokens == null || contextTokens <= 0) return messages
    val budget = contextTokens.toLong() - (replyTokens ?: 0).coerceAtLeast(0) - toolTokens.coerceAtLeast(0)
    require(budget > 0) { "最大回复量或工具定义已占满上下文，请调高上下文长度或调低最大回复量。" }
    val costs = messages.map { estimateTokens(it.toText()).toLong() + 8 }
    var total = costs.sum()
    if (total <= budget) return messages
    require(allowHistoryTrim) { "待总结的完整内容超过上下文长度，请增大上下文或分段总结；没有自动丢弃待总结消息。" }

    val latestUser = messages.indexOfLast { !it.isSynthetic && it.role == MessageRole.USER }
    if (latestUser < 0) {
        error("当前提示词超过上下文长度，请调高上下文或减少预设、角色卡和世界书内容。")
    }
    val removed = mutableSetOf<Int>()
    val older = messages.indices.filter { it < latestUser && !messages[it].isSynthetic }
    // A turn starts at each real user message; a leading greeting is a separate group.
    val groups = mutableListOf<MutableList<Int>>()
    older.forEach { index ->
        if (groups.isEmpty() || messages[index].role == MessageRole.USER) groups += mutableListOf<Int>()
        groups.last().add(index)
    }
    for (group in groups) {
        removed.addAll(group)
        total -= group.sumOf { costs[it] }
        if (total <= budget) break
    }
    require(total <= budget) {
        "预设、角色卡、世界书和当前一轮消息仍超过上下文长度，请增大上下文或减少这些内容。"
    }
    return messages.filterIndexed { index, _ -> index !in removed }
}
