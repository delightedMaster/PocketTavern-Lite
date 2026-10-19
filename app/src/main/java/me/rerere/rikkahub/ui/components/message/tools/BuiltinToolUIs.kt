package me.rerere.rikkahub.ui.components.message.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import coil3.compose.AsyncImage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.common.http.jsonObjectOrNull
import me.rerere.highlight.CodeHighlightText
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Book02
import me.rerere.hugeicons.stroke.ChartColumn
import me.rerere.hugeicons.stroke.ChartLineData01
import me.rerere.hugeicons.stroke.ChartScatter
import me.rerere.hugeicons.stroke.Eraser
import me.rerere.hugeicons.stroke.GlobalSearch
import me.rerere.hugeicons.stroke.MagicWand01
import me.rerere.hugeicons.stroke.Message02
import me.rerere.hugeicons.stroke.QuillWrite01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Time02
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.tools.MemoryToolNames
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.memoryId
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.Favicon
import me.rerere.rikkahub.ui.components.ui.FaviconRow
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.modifier.shimmer
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import me.rerere.rikkahub.utils.openUrl
import me.rerere.rikkahub.utils.toLocalString
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 记忆库工具: 标题按读/写/删区分, 摘要显示操作的文件路径, 点路径跳到该文件的详情页
 */
class MemoryFileToolUI(override val toolName: String) : ToolUIRenderer {
    override fun icon(context: ToolUIContext): ImageVector = when (toolName) {
        MemoryToolNames.READ, MemoryToolNames.LIST -> HugeIcons.Book02
        MemoryToolNames.DELETE -> HugeIcons.Eraser
        else -> HugeIcons.QuillWrite01
    }

    @Composable
    override fun title(context: ToolUIContext): String =
        memoryToolTitle(toolName, context.arguments)
            ?: stringResource(R.string.chat_message_tool_call_generic, toolName)

    // memory_read 的 path 是数组, 其余工具是单个路径
    private fun paths(context: ToolUIContext): List<String> =
        when (val path = context.arguments.jsonObjectOrNull?.get("path")) {
            is JsonArray -> path.mapNotNull { it.jsonPrimitiveOrNull?.contentOrNull }
            else -> listOfNotNull(path?.jsonPrimitiveOrNull?.contentOrNull)
        }

    override fun hasSummary(context: ToolUIContext): Boolean = paths(context).isNotEmpty()

    @Composable
    override fun Summary(context: ToolUIContext) {
        val navController = LocalNavController.current
        val paths = paths(context)
        // 参数还在生成时路径可能不完整; 删掉的文件没有详情可看
        val memoryId = context.assistant?.memoryId
            ?.takeIf { !context.loading && toolName != MemoryToolNames.DELETE }
        Column {
            paths.take(MAX_SUMMARY_PATHS).fastForEach { path ->
                Row(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(enabled = memoryId != null) {
                            memoryId?.let { navController.navigate(Screen.MemoryFile(it, path)) }
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = path,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .shimmer(isLoading = context.loading),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (memoryId != null) {
                        Icon(
                            imageVector = HugeIcons.ArrowRight01,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
            }
            if (paths.size > MAX_SUMMARY_PATHS) {
                Text(
                    text = "+${paths.size - MAX_SUMMARY_PATHS}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }

    companion object {
        private const val MAX_SUMMARY_PATHS = 5

        val all = listOf(
            MemoryToolNames.READ,
            MemoryToolNames.WRITE,
            MemoryToolNames.STR_REPLACE,
            MemoryToolNames.APPEND,
            MemoryToolNames.DELETE,
            MemoryToolNames.LIST,
        ).map(::MemoryFileToolUI)
    }
}

/** 记忆库工具的标题, 聊天消息与导出图片共用; 不是记忆库工具时返回 null */
@Composable
internal fun memoryToolTitle(toolName: String, arguments: JsonElement?): String? = when (toolName) {
    MemoryToolNames.READ -> stringResource(R.string.chat_message_tool_read_memory)
    MemoryToolNames.LIST -> stringResource(R.string.chat_message_tool_list_memory)
    MemoryToolNames.WRITE -> if (arguments.getStringContent("if_version") == MemoryRepository.NEW_FILE_VERSION) {
        stringResource(R.string.chat_message_tool_create_memory)
    } else {
        stringResource(R.string.chat_message_tool_edit_memory)
    }

    MemoryToolNames.STR_REPLACE, MemoryToolNames.APPEND -> stringResource(R.string.chat_message_tool_edit_memory)
    MemoryToolNames.DELETE -> stringResource(R.string.chat_message_tool_delete_memory)
    else -> null
}

/**
 * 网络搜索: 标题带查询词, 摘要显示 answer 与结果数, 详情为结果列表
 */
object SearchWebToolUI : ToolUIRenderer {
    override val toolName: String = "search_web"

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.Search01

    @Composable
    override fun title(context: ToolUIContext): String = stringResource(
        R.string.chat_message_tool_search_web,
        context.arguments.getStringContent("query") ?: ""
    )

    private fun items(context: ToolUIContext): List<JsonElement> =
        context.content?.jsonObjectOrNull?.get("items")?.jsonArray ?: emptyList()

    override fun hasSummary(context: ToolUIContext): Boolean =
        context.content.getStringContent("answer") != null || items(context).isNotEmpty()

    @Composable
    override fun Summary(context: ToolUIContext) {
        context.content.getStringContent("answer")?.let { answer ->
            Text(
                text = answer,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.shimmer(isLoading = context.loading),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val items = items(context)
        if (items.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FaviconRow(
                    urls = items.mapNotNull { it.getStringContent("url") },
                    size = 18.dp,
                )
                Text(
                    text = stringResource(R.string.chat_message_tool_search_results_count, items.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }

    @Composable
    override fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        val content = context.content
        if (content == null) {
            DefaultToolPreview(context = context)
            return
        }
        SearchWebPreview(arguments = context.arguments, content = content)
    }
}

/**
 * 网页抓取: 摘要显示 URL, 详情为各网页的 Markdown 内容
 */
object ScrapeWebToolUI : ToolUIRenderer {
    override val toolName: String = "scrape_web"

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.GlobalSearch

    @Composable
    override fun title(context: ToolUIContext): String =
        stringResource(R.string.chat_message_tool_scrape_web)

    override fun hasSummary(context: ToolUIContext): Boolean =
        context.arguments.getStringContent("url") != null

    @Composable
    override fun Summary(context: ToolUIContext) {
        Text(
            text = context.arguments.getStringContent("url") ?: "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
        )
    }

    @Composable
    override fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        val content = context.content
        if (content == null) {
            DefaultToolPreview(context = context)
            return
        }
        ScrapeWebPreview(content = content)
    }
}

/**
 * 获取时间信息
 */
object GetTimeInfoToolUI : ToolUIRenderer {
    override val toolName: String = "get_time_info"

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.Time02

    @Composable
    override fun title(context: ToolUIContext): String =
        stringResource(R.string.chat_message_tool_get_time)
}

/**
 * 技能调用: 标题显示技能名与路径
 */
object UseSkillToolUI : ToolUIRenderer {
    override val toolName: String = "use_skill"

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.MagicWand01

    @Composable
    override fun title(context: ToolUIContext): String {
        val skillName = context.arguments.getStringContent("name") ?: ""
        val path = context.arguments.getStringContent("path")
        return if (path != null) "Skill: $skillName / $path" else "Skill: $skillName"
    }
}

/**
 * 最近聊天: 标题固定, 摘要列出最近对话的标题
 */
object RecentChatsToolUI : ToolUIRenderer {
    override val toolName: String = "recent_chats"

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.Message02

    @Composable
    override fun title(context: ToolUIContext): String =
        stringResource(R.string.chat_message_tool_recent_chats)

    private fun chats(context: ToolUIContext): List<JsonElement> =
        (context.content as? JsonArray) ?: emptyList()

    override fun hasSummary(context: ToolUIContext): Boolean = chats(context).isNotEmpty()

    @Composable
    override fun Summary(context: ToolUIContext) {
        val titles = chats(context).mapNotNull { it.getStringContent("title") }
        if (titles.isEmpty()) return
        Text(
            text = titles.joinToString(", "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.shimmer(isLoading = context.loading),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 对话历史搜索: 标题带查询词, 摘要显示命中数
 */
object ConversationSearchToolUI : ToolUIRenderer {
    override val toolName: String = "conversation_search"

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.Search01

    @Composable
    override fun title(context: ToolUIContext): String = stringResource(
        R.string.chat_message_tool_conversation_search,
        context.arguments.getStringContent("query") ?: ""
    )

    private fun results(context: ToolUIContext): List<JsonElement> =
        (context.content as? JsonArray) ?: emptyList()

    override fun hasSummary(context: ToolUIContext): Boolean = results(context).isNotEmpty()

    @Composable
    override fun Summary(context: ToolUIContext) {
        val results = results(context)
        if (results.isEmpty()) return
        Text(
            text = stringResource(R.string.chat_message_tool_search_results_count, results.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
        )
    }
}

/**
 * 屏幕使用时间: 摘要显示总时长与用时最多的应用, 详情为按时长排序的应用列表 (带占比条);
 * 无权限时回退到默认 JSON 详情
 */
object ChartDisplayToolUI : ToolUIRenderer {
    override val toolName: String = "chart_display"

    override fun icon(context: ToolUIContext): ImageVector =
        when (context.arguments.getStringContent("style")) {
            "bar" -> HugeIcons.ChartColumn
            "scatter" -> HugeIcons.ChartScatter
            else -> HugeIcons.ChartLineData01
        }

    @Composable
    override fun title(context: ToolUIContext): String {
        val chartTitle = context.arguments.getStringContent("title")
        return if (chartTitle.isNullOrBlank()) {
            stringResource(R.string.chat_message_tool_chart_display)
        } else {
            stringResource(R.string.chat_message_tool_chart_display_with_title, chartTitle)
        }
    }
}

@Composable
private fun SearchWebPreview(
    arguments: JsonElement,
    content: JsonElement,
) {
    val context = LocalContext.current
    val items = content.jsonObject["items"]?.jsonArray ?: emptyList()
    val answer = content.getStringContent("answer")
    val query = arguments.getStringContent("query") ?: ""
    val parameters = arguments.jsonObjectOrNull?.entries?.filter { it.key != "query" }.orEmpty()
    val images = content.jsonObject["images"]?.jsonArray
        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
        ?.filter { it.isNotBlank() }
        ?: emptyList()

    LazyColumn(
        modifier = Modifier
            .fillMaxHeight(0.8f)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(stringResource(R.string.chat_message_tool_search_prefix, query))
        }

        if (parameters.isNotEmpty()) {
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    parameters.forEach { (name, value) ->
                        val displayValue = when (value) {
                            is JsonArray -> value.joinToString(", ") {
                                it.jsonPrimitiveOrNull?.contentOrNull ?: it.toString()
                            }
                            else -> value.jsonPrimitiveOrNull?.contentOrNull ?: value.toString()
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ) {
                            Text(
                                text = "$name: $displayValue",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }

        if (answer != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    MarkdownBlock(
                        content = answer,
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (images.isNotEmpty()) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(images) { imageUrl ->
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .height(120.dp)
                                .width(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { context.openUrl(imageUrl) },
                        )
                    }
                }
            }
        }

        if (items.isNotEmpty()) {
            items(items) { item ->
                val url = item.getStringContent("url") ?: return@items
                val title = item.getStringContent("title") ?: return@items
                val text = item.getStringContent("text") ?: return@items
                val publishedDate = item.getStringContent("publishedDate")?.takeIf { it.isNotBlank() }
                val dateLabel = remember(publishedDate) {
                    publishedDate?.let { value ->
                        runCatching {
                            LocalDate.parse(value, DateTimeFormatter.ISO_DATE_TIME)
                        }.recoverCatching {
                            LocalDate.parse(value, DateTimeFormatter.ISO_DATE)
                        }.getOrNull()?.toLocalString(includeYear = true) ?: value
                    }
                }

                Card(
                    onClick = { context.openUrl(url) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Favicon(
                            url = url,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(text = title, maxLines = 1)
                            if (dateLabel != null) {
                                Text(
                                    text = dateLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = url,
                                maxLines = 1,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        } else {
            item {
                CodeHighlightText(
                    code = JsonInstantPretty.encodeToString(content),
                    language = "json",
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun ScrapeWebPreview(content: JsonElement) {
    val urls = content.jsonObject["urls"]?.jsonArray ?: emptyList()

    LazyColumn(
        modifier = Modifier
            .fillMaxHeight(0.8f)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = stringResource(
                    R.string.chat_message_tool_scrape_prefix,
                    urls.joinToString(", ") { it.getStringContent("url") ?: "" }
                )
            )
        }

        items(urls) { url ->
            val urlObject = url.jsonObject
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = urlObject["url"]?.jsonPrimitive?.content ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth()
                )
                Card {
                    MarkdownBlock(
                        content = urlObject["content"]?.jsonPrimitive?.content ?: "",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}
