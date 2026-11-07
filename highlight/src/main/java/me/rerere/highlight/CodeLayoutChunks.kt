package me.rerere.highlight

/**
 * 一段代码单独做成一个 Text 时，行数一多，超出的行不会画出来。
 * 同时高亮器对超过 [HIGHLIGHT_CHAR_LIMIT] 的整段会放弃着色。
 * 按行切开，让每一段都短到可以完整排版并继续按语法着色。
 */
const val CODE_LAYOUT_MAX_LINES = 48
const val CODE_LAYOUT_MAX_CHARS = 3500
const val HIGHLIGHT_CHAR_LIMIT = 4096

fun chunkCodeForLayout(
    lines: List<String>,
    maxChars: Int = CODE_LAYOUT_MAX_CHARS,
    maxLines: Int = CODE_LAYOUT_MAX_LINES,
): List<List<String>> {
    if (lines.isEmpty()) return emptyList()
    val limitChars = maxChars.coerceAtLeast(1)
    val limitLines = maxLines.coerceAtLeast(1)
    val chunks = mutableListOf<List<String>>()
    var index = 0
    while (index < lines.size) {
        val group = mutableListOf<String>()
        var chars = 0
        while (index < lines.size && group.size < limitLines) {
            val line = lines[index]
            val extra = line.length + if (group.isEmpty()) 0 else 1
            if (group.isNotEmpty() && chars + extra > limitChars) break
            group += line
            chars += extra
            index++
            // 单行本身已经超长时单独成段，避免和后面的行粘在一起撑破高亮上限
            if (line.length >= limitChars) break
        }
        chunks += group
    }
    return chunks
}
