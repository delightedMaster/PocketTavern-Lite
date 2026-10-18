package me.rerere.ai.ui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class UIMessageAnnotation {
    @Serializable
    @SerialName("url_citation")
    data class UrlCitation(
        val title: String,
        val url: String
    ) : UIMessageAnnotation()

    /** mes_example messages. World info EMTop/EMBottom anchors use this mark. */
    @Serializable
    @SerialName("example_message")
    data object ExampleMessage : UIMessageAnnotation()

    /** Character-card field messages. before_char / after_char anchors use this mark. */
    @Serializable
    @SerialName("character_card")
    data object CharacterCardData : UIMessageAnnotation()

    /**
     * Ask the provider to put a cache breakpoint on this message.
     * Claude keeps at most four breakpoints; the provider picks the earliest and latest marks.
     */
    @Serializable
    @SerialName("cache_breakpoint")
    data class CacheBreakpoint(val point: String) : UIMessageAnnotation()

    /** Which group member produced this reply. Display only; not part of the prompt prefix. */
    @Serializable
    @SerialName("group_speaker")
    data class GroupSpeaker(val memberId: String, val name: String) : UIMessageAnnotation()
}
