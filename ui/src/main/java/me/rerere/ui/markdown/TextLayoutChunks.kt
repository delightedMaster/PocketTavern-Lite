package me.rerere.ui.markdown

import androidx.compose.ui.text.AnnotatedString

/**
 * 单个 Compose Text 的布局高度一旦超过绘制上限，多出来的行就画不出来。
 * 按这个长度切开后，每一段自己的布局都留在安全高度内，拼起来仍是原文。
 */
internal const val TEXT_LAYOUT_CHUNK_CHARS = 1200

internal fun AnnotatedString.splitForTextLayout(
    maxChars: Int = TEXT_LAYOUT_CHUNK_CHARS,
): List<AnnotatedString> {
    if (maxChars <= 0 || length <= maxChars) return listOf(this)
    return layoutChunkRanges(text, maxChars).map { (start, endExclusive) ->
        subSequence(start, endExclusive)
    }
}

/**
 * 半开区间。优先在换行处切开；切开点落在代理对中间时退回，避免拆开一个字符。
 */
internal fun layoutChunkRanges(text: String, maxChars: Int): List<Pair<Int, Int>> {
    if (text.isEmpty() || maxChars <= 0 || text.length <= maxChars) return listOf(0 to text.length)
    val ranges = mutableListOf<Pair<Int, Int>>()
    var start = 0
    while (start < text.length) {
        var end = (start + maxChars).coerceAtMost(text.length)
        if (end < text.length) {
            val newline = text.lastIndexOf('\n', end - 1)
            if (newline > start) end = newline + 1
            end = retreatFromSurrogate(text, end)
            if (end <= start) end = nextCodePointEnd(text, start)
        }
        ranges += start to end
        start = end
    }
    return ranges
}

private fun retreatFromSurrogate(text: String, index: Int): Int {
    if (index <= 0 || index >= text.length) return index
    return if (text[index].isLowSurrogate() && text[index - 1].isHighSurrogate()) index - 1 else index
}

private fun nextCodePointEnd(text: String, index: Int): Int {
    if (index >= text.length) return text.length
    return if (text[index].isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) {
        index + 2
    } else {
        index + 1
    }
}
