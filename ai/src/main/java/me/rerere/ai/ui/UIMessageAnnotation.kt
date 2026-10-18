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
}
