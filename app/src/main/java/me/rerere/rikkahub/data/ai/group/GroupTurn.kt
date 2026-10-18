package me.rerere.rikkahub.data.ai.group

import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.GroupActivationStrategy
import me.rerere.rikkahub.data.model.GroupChat
import me.rerere.rikkahub.data.model.assembleCharacterCardMessages

/**
 * Group member cards are emitted in [GroupChat.memberIds] order, including members
 * who are muted. The speaker's name is a separate tail line, so picking someone
 * else does not rewrite the prefix. The chat's memory lorebook is the one on the
 * conversation, shared by every member.
 *
 * Natural order does not call the model. A single mentioned member speaks next;
 * otherwise the list order is used.
 */
const val GROUP_PREAMBLE =
    "This is a group chat. Character cards stay in this fixed order. Reply only as the speaker named in the latest instruction."

data class GroupMemberView(
    val id: String,
    val name: String,
    val card: String,
    val weight: Int = 1,
    val disabled: Boolean = false,
)

data class SpeakerChoice(val id: String, val name: String)

fun Assistant.toGroupMember(group: GroupChat): GroupMemberView {
    val card = assembleCharacterCardMessages().joinToString("\n") { it.toText() }.ifBlank { systemPrompt }
    return GroupMemberView(
        id = id.toString(),
        name = name.ifBlank { "Character" },
        card = card,
        weight = group.speakerWeights[id] ?: 1,
        disabled = id in group.disabledMemberIds,
    )
}

fun groupCardLines(members: List<GroupMemberView>): List<String> =
    members.map { member -> "Character ${member.name}:\n${member.card}" }

fun speakerCue(name: String): String =
    "Current speaker: $name. Write only $name's next reply, in character."

fun nextGroupSpeaker(
    strategy: GroupActivationStrategy,
    members: List<GroupMemberView>,
    lastSpeakerId: String?,
    lastMessage: String,
    manualSpeakerId: String?,
    allowSelfResponses: Boolean,
    randomDraw: Int,
): SpeakerChoice? {
    if (members.isEmpty()) return null
    val enabled = members.filter { !it.disabled }
    if (enabled.isEmpty()) return null
    val pool = if (!allowSelfResponses && lastSpeakerId != null && enabled.size > 1) {
        enabled.filter { it.id != lastSpeakerId }
    } else {
        enabled
    }
    val chosen = when (strategy) {
        GroupActivationStrategy.MANUAL ->
            pool.firstOrNull { it.id == manualSpeakerId } ?: pool.first()
        GroupActivationStrategy.LIST -> nextInList(members, pool, lastSpeakerId)
        GroupActivationStrategy.POOLED -> pickWeighted(pool, randomDraw)
        GroupActivationStrategy.NATURAL -> naturalSpeaker(pool, lastMessage)
            ?: nextInList(members, pool, lastSpeakerId)
    }
    return SpeakerChoice(chosen.id, chosen.name)
}

private fun nextInList(
    members: List<GroupMemberView>,
    pool: List<GroupMemberView>,
    lastSpeakerId: String?,
): GroupMemberView {
    val start = members.indexOfFirst { it.id == lastSpeakerId }
    val rotated = if (start < 0) members else members.drop(start + 1) + members.take(start + 1)
    return rotated.firstOrNull { it.id in pool.map { member -> member.id } } ?: pool.first()
}

private fun naturalSpeaker(pool: List<GroupMemberView>, lastMessage: String): GroupMemberView? {
    val mentioned = pool
        .filter { it.name.isNotBlank() && lastMessage.contains(it.name, ignoreCase = true) }
        .sortedByDescending { it.name.length }
    if (mentioned.isEmpty()) return null
    val longest = mentioned.first().name.length
    val ties = mentioned.filter { it.name.length == longest }
    return ties.singleOrNull()
}

private fun pickWeighted(pool: List<GroupMemberView>, draw: Int): GroupMemberView {
    val weights = pool.map { it.weight.coerceAtLeast(1) }
    val total = weights.sum().coerceAtLeast(1)
    var cursor = draw % total
    if (cursor < 0) cursor += total
    var acc = 0
    for (index in pool.indices) {
        acc += weights[index]
        if (cursor < acc) return pool[index]
    }
    return pool.last()
}
