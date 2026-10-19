package me.rerere.rikkahub.data.ai.cards

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI

/**
 * Sandbox-side contract for HTML front-end cards.
 *
 * Supported 酒馆助手 (JS-Slash-Runner) calls, injected by [TAVERN_CARD_SHIM]:
 * - `getChatMessages(range)` — stored message text. `range` may be omitted,
 *   `"all"`, a number (`-1` is the last message, `0` is the first), `"start-end"`,
 *   or `{start, end}`.
 * - `getVariables({type})` — `chat` (default), `global`, or `message` (this card's message).
 * - `setVariables(vars, {type})`, `replaceVariables`, and `insertOrAssignVariables`
 *   merge keys. They do not wipe the rest of the table.
 * - `triggerSlash(command)` — the same basic slash commands as quick replies.
 * - `Mvu.getMvuData({type})` and `Mvu.replaceMvuData(data, {type})` — `chat` or `message`.
 *   The JSON is stored as the variable `mvu.stat_data`.
 *
 * Chat, global, and the latest message's variables are rendered in the dynamic
 * tail. They are not written into the system prompt or the character card.
 * Anything else (generateRaw, eventEmit, SillyTavern.getContext, iframe messaging)
 * is absent on purpose.
 */
const val TAVERN_CARD_SHIM = """
(function () {
  function call(op, payload) {
    if (!window.PocketTavern || !window.PocketTavern.handle) return null;
    var raw = window.PocketTavern.handle(JSON.stringify({ op: op, payload: payload || {} }));
    var parsed = {};
    try { parsed = JSON.parse(raw || '{}'); } catch (e) { parsed = { error: String(e) }; }
    if (parsed.error) throw new Error(parsed.error);
    return parsed.result;
  }
  window.getChatMessages = function (range) { return call('getChatMessages', { range: range === undefined ? null : range }); };
  window.getVariables = function (options) { return call('getVariables', options || { type: 'chat' }); };
  window.setVariables = function (vars, options) { return call('setVariables', { variables: vars || {}, type: (options && options.type) || 'chat' }); };
  window.replaceVariables = window.setVariables;
  window.insertOrAssignVariables = window.setVariables;
  window.triggerSlash = function (command) { return call('triggerSlash', { command: String(command || '') }); };
  window.Mvu = {
    getMvuData: function (options) { return call('mvuGet', options || { type: 'chat' }); },
    replaceMvuData: function (data, options) { return call('mvuReplace', { data: data, type: (options && options.type) || 'message' }); }
  };
  function report() {
    var body = document.body;
    var root = document.documentElement;
    var h = Math.max(body ? body.scrollHeight : 0, root ? root.scrollHeight : 0);
    if (window.PocketTavern && window.PocketTavern.reportHeight) window.PocketTavern.reportHeight(h);
  }
  window.addEventListener('load', report);
  if (window.ResizeObserver && document.body) new ResizeObserver(report).observe(document.body);
  setTimeout(report, 0);
})();
"""

private val CARD_TAG = Regex(
    """<(?:html|body|div|style|script|iframe|table|section|canvas)\b""",
    RegexOption.IGNORE_CASE,
)

const val MVU_VARIABLE = "mvu.stat_data"

fun looksLikeCardHtml(text: String): Boolean = CARD_TAG.containsMatchIn(text)

/** Full document with the shim in the head. The card cannot see app storage or keys. */
fun cardDocument(source: String): String {
    val shim = "<script>$TAVERN_CARD_SHIM</script>"
    val trimmed = source.trim()
    val head = Regex("<head[^>]*>", RegexOption.IGNORE_CASE)
    val html = Regex("<html[^>]*>", RegexOption.IGNORE_CASE)
    if (html.containsMatchIn(trimmed)) {
        return if (head.containsMatchIn(trimmed)) {
            head.replaceFirst(trimmed, "$0$shim")
        } else {
            html.replaceFirst(trimmed, "$0<head>$shim</head>")
        }
    }
    return """<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">$shim<style>html,body{margin:0;padding:0;background:transparent;}</style></head><body>$trimmed</body></html>"""
}

/** data/about are local. http(s) only when the host is on the allowlist. file and javascript are blocked. */
fun cardRequestAllowed(url: String, allowlist: List<String>): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase() ?: return false
    if (scheme == "javascript" || scheme == "file" || scheme == "content") return false
    if (scheme == "data" || scheme == "about") return true
    if (scheme != "https" && scheme != "http") return false
    val host = uri.host?.lowercase() ?: return false
    return allowlist.any { pattern ->
        val allowed = pattern.trim().lowercase()
        allowed.isNotEmpty() && (host == allowed || host.endsWith(".$allowed"))
    }
}

data class CardMessage(
    val id: String,
    val role: String,
    val text: String,
)

data class CardWorld(
    val messages: List<CardMessage>,
    val chatVariables: Map<String, String> = emptyMap(),
    val globalVariables: Map<String, String> = emptyMap(),
    val messageVariables: Map<String, Map<String, String>> = emptyMap(),
    val currentMessageId: String,
)

data class CardEffect(
    val responseJson: String,
    val chatVariables: Map<String, String>? = null,
    val globalVariables: Map<String, String>? = null,
    val messageVariables: Map<String, Map<String, String>>? = null,
    val slash: String? = null,
)

fun handleCardRequest(world: CardWorld, requestJson: String): CardEffect {
    val root = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(requestJson).jsonObject }.getOrNull()
        ?: return CardEffect(errorJson("bad request"))
    val op = root["op"]?.jsonPrimitive?.contentOrNull ?: return CardEffect(errorJson("missing op"))
    val payload = root["payload"] as? JsonObject ?: JsonObject(emptyMap())
    return when (op) {
        "getChatMessages" -> CardEffect(okJson(messagesJson(selectMessages(world.messages, payload["range"]))))
        "getVariables" -> CardEffect(okJson(variablesJson(variablesOf(world, scopeOf(payload)))))
        "setVariables", "replaceVariables" -> assignVariables(world, payload)
        "triggerSlash" -> CardEffect(
            responseJson = okJson(JsonPrimitive(true)),
            slash = payload["command"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
        "mvuGet" -> {
            val raw = variablesOf(world, mvuScope(payload))[MVU_VARIABLE]
            val parsed = raw?.let { runCatching { kotlinx.serialization.json.Json.parseToJsonElement(it) }.getOrNull() }
                ?: JsonObject(emptyMap())
            CardEffect(okJson(parsed))
        }
        "mvuReplace" -> {
            val data = payload["data"] ?: JsonObject(emptyMap())
            assignFlat(world, mvuScope(payload), mapOf(MVU_VARIABLE to data.toString()))
        }
        else -> CardEffect(errorJson("unsupported: $op"))
    }
}

private fun assignVariables(world: CardWorld, payload: JsonObject): CardEffect {
    val incoming = payload["variables"] as? JsonObject ?: JsonObject(emptyMap())
    val flat = incoming.mapNotNull { (key, value) ->
        val text = when (value) {
            is JsonNull -> return@mapNotNull null
            is JsonPrimitive -> value.contentOrNull ?: value.toString()
            else -> value.toString()
        }
        key to text
    }.toMap()
    return assignFlat(world, scopeOf(payload), flat)
}

private fun assignFlat(world: CardWorld, scope: String, updates: Map<String, String>): CardEffect {
    val merged = variablesOf(world, scope) + updates
    val response = okJson(variablesJson(merged))
    return when (scope) {
        "global" -> CardEffect(response, globalVariables = merged)
        "message" -> {
            val all = world.messageVariables.toMutableMap()
            all[world.currentMessageId] = merged
            CardEffect(response, messageVariables = all)
        }
        else -> CardEffect(response, chatVariables = merged)
    }
}

private fun variablesOf(world: CardWorld, scope: String): Map<String, String> = when (scope) {
    "global" -> world.globalVariables
    "message" -> world.messageVariables[world.currentMessageId].orEmpty()
    else -> world.chatVariables
}

private fun scopeOf(payload: JsonObject): String =
    payload["type"]?.jsonPrimitive?.contentOrNull ?: "chat"

private fun mvuScope(payload: JsonObject): String {
    val type = payload["type"]?.jsonPrimitive?.contentOrNull ?: "chat"
    return if (type == "message") "message" else "chat"
}

fun selectMessages(messages: List<CardMessage>, range: JsonElement?): List<CardMessage> {
    if (range == null || range is JsonNull) return messages
    if (range is JsonPrimitive && range.isString) {
        val text = range.content
        if (text.isBlank() || text == "all") return messages
        val parts = text.split('-', limit = 2)
        if (parts.size == 2 && parts[0].toIntOrNull() != null && parts[1].toIntOrNull() != null) {
            return slice(messages, parts[0].toInt(), parts[1].toInt())
        }
    }
    if (range is JsonPrimitive) {
        val index = range.intOrNull ?: return messages
        return if (index < 0) {
            messages.takeLast((-index).coerceAtMost(messages.size))
        } else {
            listOfNotNull(messages.getOrNull(index))
        }
    }
    if (range is JsonObject) {
        val start = range["start"]?.jsonPrimitive?.intOrNull ?: 0
        val end = range["end"]?.jsonPrimitive?.intOrNull ?: messages.lastIndex
        return slice(messages, start, end)
    }
    return messages
}

private fun slice(messages: List<CardMessage>, start: Int, end: Int): List<CardMessage> {
    if (messages.isEmpty()) return emptyList()
    val from = start.coerceIn(0, messages.lastIndex)
    val to = (end + 1).coerceIn(from, messages.size)
    return messages.subList(from, to)
}

private fun messagesJson(messages: List<CardMessage>): JsonArray = buildJsonArray {
    messages.forEach { message ->
        add(buildJsonObject {
            put("message_id", message.id)
            put("role", message.role)
            put("message", message.text)
        })
    }
}

private fun variablesJson(variables: Map<String, String>): JsonObject = buildJsonObject {
    variables.toSortedMap().forEach { (key, value) -> put(key, value) }
}

private fun okJson(result: JsonElement): String = buildJsonObject { put("result", result) }.toString()

private fun errorJson(message: String): String = buildJsonObject { put("error", message) }.toString()
