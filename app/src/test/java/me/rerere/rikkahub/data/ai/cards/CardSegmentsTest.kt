package me.rerere.rikkahub.data.ai.cards

import me.rerere.rikkahub.data.ai.cards.CardSegment.Html
import me.rerere.rikkahub.data.ai.cards.CardSegment.Prose
import org.junit.Assert.assertEquals
import org.junit.Test

class CardSegmentsTest {
    @Test
    fun `status bar after the story renders in place`() {
        val text = "她推开门。\n\n<style>.bar{color:red}</style>\n<div class=\"bar\"><div>HP 10</div></div>\n后文"
        assertEquals(
            listOf(
                Prose("她推开门。\n\n"),
                Html("<style>.bar{color:red}</style>\n<div class=\"bar\"><div>HP 10</div></div>"),
                Prose("\n后文"),
            ),
            splitCardSegments(text),
        )
    }

    @Test
    fun `full documents in the middle of a message become one card`() {
        val text = "前言\n<!DOCTYPE html>\n<html><body><p>x</p></body></html>"
        assertEquals(
            listOf(Prose("前言\n"), Html("<!DOCTYPE html>\n<html><body><p>x</p></body></html>")),
            splitCardSegments(text),
        )
    }

    @Test
    fun `unclosed blocks inline tags and fenced code stay prose`() {
        val streaming = "正文\n<div class=\"bar\">HP"
        assertEquals(listOf(Prose(streaming)), splitCardSegments(streaming))

        val inline = "用 <div> 包起来就行"
        assertEquals(listOf(Prose(inline)), splitCardSegments(inline))

        val fenced = "看代码：\n```\n<div>x</div>\n```\n"
        assertEquals(listOf(Prose(fenced)), splitCardSegments(fenced))
    }

    @Test
    fun `separate blocks with prose between become separate cards`() {
        val text = "<div>a</div>\n中间\n<table><tr><td>b</td></tr></table>"
        assertEquals(
            listOf(Html("<div>a</div>"), Prose("\n中间\n"), Html("<table><tr><td>b</td></tr></table>")),
            splitCardSegments(text),
        )
    }
}
