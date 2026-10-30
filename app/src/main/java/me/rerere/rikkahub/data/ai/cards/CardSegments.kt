package me.rerere.rikkahub.data.ai.cards

/** A piece of a message: Markdown prose, or raw HTML that renders as a card in place. */
sealed interface CardSegment {
    val text: String

    data class Prose(override val text: String) : CardSegment
    data class Html(override val text: String) : CardSegment
}

private val BLOCK_START = Regex(
    """^<(?:!doctype\s+html|html|body|div|style|script|iframe|table|section|canvas)\b""",
    RegexOption.IGNORE_CASE,
)
private val TAG_NAME = Regex("""^<(!doctype|[a-zA-Z][a-zA-Z0-9-]*)""", RegexOption.IGNORE_CASE)
private val FENCE = Regex("""^ {0,3}(`{3,}|~{3,})""")

/**
 * Splits prose from raw HTML blocks a preset writes after the story text, such as a status bar
 * `<style>…</style><div>…</div>`. A block starts on a line that begins with a card tag outside
 * a code fence and runs to its matching end tag; touching blocks are kept in one card so a
 * `<style>` still reaches the markup after it. A block without its end tag stays prose, so a
 * half-streamed card shows as text until it closes.
 */
fun splitCardSegments(text: String): List<CardSegment> {
    val segments = mutableListOf<CardSegment>()
    var proseStart = 0
    var lineStart = 0
    var fence: String? = null
    while (lineStart < text.length) {
        val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
        val line = text.substring(lineStart, lineEnd)
        val fenceMarker = FENCE.find(line)?.groupValues?.get(1)
        if (fence != null) {
            if (fenceMarker != null && fenceMarker[0] == fence[0] && fenceMarker.length >= fence.length &&
                line.trim() == fenceMarker
            ) {
                fence = null
            }
        } else if (fenceMarker != null) {
            fence = fenceMarker
        } else {
            val tagStart = lineStart + (line.length - line.trimStart().length)
            val blockEnd = if (BLOCK_START.containsMatchIn(text.substring(tagStart, lineEnd))) {
                htmlRunEnd(text, tagStart)
            } else null
            if (blockEnd != null) {
                if (tagStart > proseStart) segments += CardSegment.Prose(text.substring(proseStart, tagStart))
                segments += CardSegment.Html(text.substring(tagStart, blockEnd))
                proseStart = blockEnd
                lineStart = blockEnd
                continue
            }
        }
        lineStart = lineEnd + 1
    }
    if (proseStart < text.length) segments += CardSegment.Prose(text.substring(proseStart))
    return segments.filterNot { it is CardSegment.Prose && it.text.isBlank() }
}

/** End of one or more touching HTML blocks starting at [start], or null if the first is unclosed. */
private fun htmlRunEnd(text: String, start: Int): Int? {
    var end = elementEnd(text, start) ?: return null
    while (true) {
        var next = end
        while (next < text.length && text[next].isWhitespace()) next++
        if (next >= text.length || !BLOCK_START.containsMatchIn(text.substring(next, minOf(text.length, next + 20)))) {
            return end
        }
        end = elementEnd(text, next) ?: return end
    }
}

private fun elementEnd(text: String, start: Int): Int? {
    val name = TAG_NAME.find(text.substring(start, minOf(text.length, start + 40)))
        ?.groupValues?.get(1)?.lowercase() ?: return null
    return when (name) {
        "!doctype" -> closingTagEnd(text, "html", start)
        "script", "style" -> closingTagEnd(text, name, start)
        else -> nestedElementEnd(text, name, start)
    }
}

private fun closingTagEnd(text: String, name: String, from: Int): Int? {
    val close = text.indexOf("</$name", from, ignoreCase = true)
    if (close < 0) return null
    val gt = text.indexOf('>', close)
    return if (gt < 0) null else gt + 1
}

private fun nestedElementEnd(text: String, name: String, start: Int): Int? {
    val tag = Regex("""<(/?)${Regex.escape(name)}\b[^>]*?(/?)>""", RegexOption.IGNORE_CASE)
    var depth = 0
    for (match in tag.findAll(text, start)) {
        val closing = match.groupValues[1].isNotEmpty()
        val selfClosing = match.groupValues[2].isNotEmpty()
        when {
            closing -> depth--
            !selfClosing -> depth++
        }
        if (depth <= 0) return match.range.last + 1
    }
    return null
}
