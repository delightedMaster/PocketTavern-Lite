package me.rerere.rikkahub.data.ai.cards

/**
 * The quick-reply commands this app runs. This is not STscript: no closures,
 * no `/if`, no `/run`. Commands are separated by newlines or `|`.
 *
 * - `/send text` sends text and generates a reply
 * - `/trigger name` runs another quick reply with that label, up to a few levels
 * - `/setvar key value` or `/setvar key=value` writes a chat variable
 * - `/getvar key` puts the value into the composer
 * - `/gen` generates if the last message is from the user; `/gen text` sends that text
 * - `/input prompt` asks for a line and puts it in the composer
 *
 * A line without a slash is sent as a user message.
 */
const val SLASH_MAX_DEPTH = 4

val SUPPORTED_SLASH_COMMANDS = setOf("send", "trigger", "setvar", "getvar", "gen", "input")

sealed interface SlashAction {
    data class Send(val text: String) : SlashAction
    data class Generate(val text: String?) : SlashAction
    data class SetVar(val key: String, val value: String) : SlashAction
    data class FillInput(val text: String) : SlashAction
    data class AskInput(val prompt: String) : SlashAction
    data class Unknown(val line: String) : SlashAction
}

data class SlashContext(
    val replies: List<Pair<String, String>> = emptyList(),
    val variables: Map<String, String> = emptyMap(),
    val depth: Int = 0,
)

fun compileSlashScript(script: String, context: SlashContext = SlashContext()): List<SlashAction> {
    if (context.depth > SLASH_MAX_DEPTH) return emptyList()
    return splitSlashScript(script).flatMap { line -> compileLine(line, context) }
}

fun splitSlashScript(script: String): List<String> =
    script.split(Regex("\\r?\\n|\\|")).map { it.trim() }.filter { it.isNotEmpty() }

private fun compileLine(line: String, context: SlashContext): List<SlashAction> {
    if (!line.startsWith("/")) return listOf(SlashAction.Send(line))
    val body = line.removePrefix("/").trim()
    val name = body.substringBefore(' ').substringBefore('\t').lowercase()
    val args = if (body.any { it.isWhitespace() }) body.substringAfter(body.substringBefore(' ').substringBefore('\t')).trim() else ""
    if (name !in SUPPORTED_SLASH_COMMANDS) return listOf(SlashAction.Unknown(line))
    return when (name) {
        "send" -> listOf(SlashAction.Send(args))
        "gen" -> listOf(SlashAction.Generate(args.ifBlank { null }))
        "input" -> listOf(SlashAction.AskInput(args.ifBlank { "" }))
        "getvar" -> listOf(SlashAction.FillInput(context.variables[args].orEmpty()))
        "setvar" -> {
            val (key, value) = splitSetVar(args) ?: return listOf(SlashAction.Unknown(line))
            listOf(SlashAction.SetVar(key, value))
        }
        "trigger" -> {
            val match = context.replies.firstOrNull { (title, _) -> title.equals(args, ignoreCase = true) }
                ?: return listOf(SlashAction.Unknown(line))
            compileSlashScript(match.second, context.copy(depth = context.depth + 1))
        }
        else -> listOf(SlashAction.Unknown(line))
    }
}

fun splitSetVar(args: String): Pair<String, String>? {
    val trimmed = args.trim()
    if (trimmed.isEmpty()) return null
    val eq = trimmed.indexOf('=')
    if (eq > 0 && !trimmed.take(eq).any { it.isWhitespace() }) {
        return trimmed.substring(0, eq).trim() to trimmed.substring(eq + 1)
    }
    val space = trimmed.indexOf(' ')
    if (space <= 0) return trimmed to ""
    return trimmed.substring(0, space).trim() to trimmed.substring(space + 1).trim()
}
