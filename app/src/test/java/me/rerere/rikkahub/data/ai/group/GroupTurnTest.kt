package me.rerere.rikkahub.data.ai.group

import me.rerere.rikkahub.data.ai.assembly.AssemblyRequest
import me.rerere.rikkahub.data.ai.assembly.HistoryMessage
import me.rerere.rikkahub.data.ai.assembly.PromptLayer
import me.rerere.rikkahub.data.ai.assembly.assemblePrompt
import me.rerere.rikkahub.data.model.GroupActivationStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupTurnTest {
    private val members = listOf(
        GroupMemberView("a", "Ada", "card-a", weight = 1),
        GroupMemberView("b", "Bea", "card-b", weight = 3),
        GroupMemberView("c", "Ada", "card-c", weight = 1, disabled = true),
    )

    @Test
    fun naturalPicksTheLongestUniqueMentionAndOtherwiseFollowsTheList() {
        val named = nextGroupSpeaker(
            GroupActivationStrategy.NATURAL,
            members,
            lastSpeakerId = "a",
            lastMessage = "Bea, look at this",
            manualSpeakerId = null,
            allowSelfResponses = false,
            randomDraw = 0,
        )
        assertEquals("b", named?.id)
        val tied = nextGroupSpeaker(
            GroupActivationStrategy.NATURAL,
            members,
            lastSpeakerId = null,
            lastMessage = "Ada said hello",
            manualSpeakerId = null,
            allowSelfResponses = true,
            randomDraw = 0,
        )
        assertEquals("a", tied?.id)
    }

    @Test
    fun listSkipsMutedMembersAndThePreviousSpeaker() {
        val first = nextGroupSpeaker(
            GroupActivationStrategy.LIST,
            members,
            lastSpeakerId = "a",
            lastMessage = "",
            manualSpeakerId = null,
            allowSelfResponses = false,
            randomDraw = 0,
        )
        assertEquals("b", first?.id)
        val wrapped = nextGroupSpeaker(
            GroupActivationStrategy.LIST,
            members,
            lastSpeakerId = "b",
            lastMessage = "",
            manualSpeakerId = null,
            allowSelfResponses = false,
            randomDraw = 0,
        )
        assertEquals("a", wrapped?.id)
    }

    @Test
    fun pooledUsesWeightsAndManualUsesTheChosenId() {
        val pooled = nextGroupSpeaker(
            GroupActivationStrategy.POOLED,
            members.filter { !it.disabled },
            lastSpeakerId = null,
            lastMessage = "",
            manualSpeakerId = null,
            allowSelfResponses = true,
            randomDraw = 1,
        )
        assertEquals("b", pooled?.id)
        val manual = nextGroupSpeaker(
            GroupActivationStrategy.MANUAL,
            members,
            lastSpeakerId = "a",
            lastMessage = "",
            manualSpeakerId = "b",
            allowSelfResponses = false,
            randomDraw = 0,
        )
        assertEquals("Bea", manual?.name)
    }

    @Test
    fun mutedCardsStayInThePrefixWhileTheSpeakerCueAndVariablesStayInTheTail() {
        val cards = groupCardLines(members)
        assertEquals(3, cards.size)
        assertTrue(cards[2].contains("card-c"))
        val ada = assemblePrompt(
            AssemblyRequest(
                staticLayers = listOf(GROUP_PREAMBLE) + cards,
                history = listOf(HistoryMessage("u1", "user", "hello")),
                stateVars = "chat.mood=calm",
                speakerText = speakerCue("Ada"),
            ),
        )
        val bea = assemblePrompt(
            AssemblyRequest(
                staticLayers = listOf(GROUP_PREAMBLE) + cards,
                history = listOf(HistoryMessage("u1", "user", "hello")),
                stateVars = "chat.mood=tense",
                speakerText = speakerCue("Bea"),
            ),
        )
        assertEquals(ada.stablePrefix, bea.stablePrefix)
        assertTrue(ada.stablePrefix.containsAll(cards))
        assertFalse(ada.stablePrefix.any { it.contains("Current speaker") || it.contains("chat.mood") })
        assertTrue(ada.pieces.any { it.layer == PromptLayer.TAIL && it.text.contains("chat.mood=calm") })
        assertTrue(bea.pieces.any { it.layer == PromptLayer.TAIL && it.text == speakerCue("Bea") })
    }
}
