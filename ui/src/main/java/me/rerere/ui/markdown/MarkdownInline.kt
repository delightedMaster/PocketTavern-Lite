package me.rerere.ui.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.util.fastForEach

internal class MarkdownContext(
    val theme: MarkdownTheme,
    val components: MarkdownComponents,
    /** 为 null 时链接交给系统的 UriHandler 打开 */
    val linkListener: LinkInteractionListener?,
)

internal val LocalMarkdownContext = staticCompositionLocalOf<MarkdownContext> {
    error("LocalMarkdownContext is only available inside Markdown()")
}

/** 把一组行内节点渲染成一个 Text */
@Composable
internal fun InlineText(content: List<MarkdownInline>, modifier: Modifier = Modifier) {
    val context = LocalMarkdownContext.current
    val style = LocalTextStyle.current
    val density = LocalDensity.current
    val contentColor = style.color.takeOrElse { LocalContentColor.current }
    val rendered = remember(content, context, style, density, contentColor) {
        InlineTextBuilder(context, MarkdownInlineScope(style, density, contentColor)).build(content)
    }
    val chunks = remember(rendered.text) { rendered.text.splitForTextLayout() }
    val textStyle = if (rendered.hasMathWidget) style.copy(lineHeight = TextUnit.Unspecified) else style
    if (chunks.size == 1) {
        LayoutText(chunks.first(), rendered.inlineContent, textStyle, contentColor, modifier)
    } else {
        // 段与段之间的换行已经留在上一截末尾，这里不再加间距，避免多出空行
        Column(modifier) {
            chunks.forEachIndexed { index, chunk ->
                LayoutText(
                    text = chunk,
                    inlineContent = rendered.inlineContent,
                    style = textStyle,
                    contentColor = contentColor,
                    // 换行留在字符串里，复制和选择才不会在分段处丢行。末行是这个换行撑出来的空行，布局时收掉。
                    clipTrailingBreak = index < chunks.lastIndex && chunk.text.endsWith('\n'),
                )
            }
        }
    }
}

@Composable
private fun LayoutText(
    text: AnnotatedString,
    inlineContent: Map<String, InlineTextContent>,
    style: TextStyle,
    contentColor: Color,
    modifier: Modifier = Modifier,
    clipTrailingBreak: Boolean = false,
) {
    var trailingBreakPx by remember(text) { mutableIntStateOf(0) }
    Text(
        text = text,
        modifier = modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val height = (placeable.height - trailingBreakPx).coerceAtLeast(0)
            layout(placeable.width, height) { placeable.place(0, 0) }
        },
        inlineContent = inlineContent,
        // 段落默认色跟随主题。Span 上的颜色（正则 / HTML / 链接）会盖过它；两边都没指定时不另填黑或白。
        color = contentColor,
        style = style,
        onTextLayout = { result ->
            val next = if (clipTrailingBreak && result.lineCount > 0) {
                val last = result.lineCount - 1
                (result.getLineBottom(last) - result.getLineTop(last)).toInt().coerceAtLeast(0)
            } else {
                0
            }
            if (next != trailingBreakPx) trailingBreakPx = next
        },
    )
}

private class RenderedInlineText(
    val text: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>,
    val hasMathWidget: Boolean,
)

private val MATH_SOURCE_STYLE = SpanStyle(fontFamily = FontFamily.Monospace)

// 开引号 -> 闭引号。单引号常被当作撇号（don't、it’s）使用，不参与配对
private val QUOTE_PAIRS = mapOf('"' to '"', '“' to '”', '「' to '」', '『' to '』')

private const val OPEN_FULLWIDTH_PAREN = '（'
private const val CLOSE_FULLWIDTH_PAREN = '）'

private const val ROLE_NORMAL: Byte = 0
private const val ROLE_QUOTE: Byte = 1
private const val ROLE_PAREN: Byte = 2
private const val ROLE_HIDDEN: Byte = 3

/** 供单测直接验证行内引号上色与全角括号隐藏逻辑，不依赖 Compose 运行时。 */
internal fun buildInlineAnnotatedText(
    content: List<MarkdownInline>,
    theme: MarkdownTheme,
    components: MarkdownComponents = MarkdownDefaults.components,
    linkListener: LinkInteractionListener? = null,
    style: TextStyle = TextStyle.Default,
    density: androidx.compose.ui.unit.Density = androidx.compose.ui.unit.Density(1f),
    contentColor: Color = Color.Unspecified,
): AnnotatedString = InlineTextBuilder(
    context = MarkdownContext(theme, components, linkListener),
    scope = MarkdownInlineScope(style, density, contentColor),
).build(content).text

private class InlineTextBuilder(
    private val context: MarkdownContext,
    private val scope: MarkdownInlineScope,
) {
    private val builder = AnnotatedString.Builder()
    private val inlineContent = mutableMapOf<String, InlineTextContent>()
    private var hasMathWidget = false
    private var flatRoles: ByteArray? = null
    private var flatCursor = 0

    fun build(content: List<MarkdownInline>): RenderedInlineText {
        val quoteStyle = context.theme.quotedText
        val parenStyle = context.theme.parenthesisText
        if (quoteStyle != null || parenStyle != null) {
            val flat = StringBuilder()
            collectFormattableText(content, flat)
            if (flat.isNotEmpty()) {
                flatRoles = computeCharRoles(
                    text = flat,
                    enableQuotes = quoteStyle != null,
                    enableParens = parenStyle != null,
                    hideParens = context.theme.hideParentheses,
                )
            }
        }
        append(content, allowFormatting = true)
        return RenderedInlineText(builder.toAnnotatedString(), inlineContent, hasMathWidget)
    }

    private fun collectFormattableText(nodes: List<MarkdownInline>, out: StringBuilder) {
        nodes.fastForEach { node ->
            when (node) {
                is MarkdownInline.Text -> out.append(node.text)
                is MarkdownInline.Styled -> {
                    // 显式指定了文字颜色（如正则/HTML 着色）的子树保留自身颜色，不参与对话/括号重着色
                    if (node.style.color == Color.Unspecified) {
                        collectFormattableText(node.children, out)
                    }
                }
                // 代码、链接、公式、图片保持原样，不参与对话/括号匹配
                is MarkdownInline.Code,
                is MarkdownInline.Link,
                is MarkdownInline.Math,
                is MarkdownInline.Image -> Unit
            }
        }
    }

    private fun computeCharRoles(
        text: CharSequence,
        enableQuotes: Boolean,
        enableParens: Boolean,
        hideParens: Boolean,
    ): ByteArray {
        val roles = ByteArray(text.length)

        // 先匹配中文全角括号（…）：用下标栈就近配对，既支持嵌套（外层闭合时自动恢复内层括号，
        // 仅隐藏最外层），也避免前面孤立的未闭合（导致后续合法的（…）全部失效。
        if (enableParens) {
            val openStack = ArrayDeque<Int>()
            val bracketRole = if (hideParens) ROLE_HIDDEN else ROLE_PAREN
            for (i in 0 until text.length) {
                when (text[i]) {
                    OPEN_FULLWIDTH_PAREN -> openStack.addLast(i)
                    CLOSE_FULLWIDTH_PAREN -> {
                        if (openStack.isNotEmpty()) {
                            val openPos = openStack.removeLast()
                            var hasVisibleContent = false
                            for (k in (openPos + 1) until i) {
                                if (!text[k].isWhitespace()) {
                                    hasVisibleContent = true
                                    break
                                }
                            }
                            if (hasVisibleContent) {
                                for (k in (openPos + 1) until i) {
                                    roles[k] = ROLE_PAREN
                                }
                                roles[openPos] = bracketRole
                                roles[i] = bracketRole
                            }
                        }
                    }
                }
            }
        }

        // 再匹配对话引号：跳过已归属括号的区域，避免括号内的孤立引号打乱外部对话配对，
        // 同时让引号内部夹杂的括号保持灰色且不破坏外层引号的闭合。
        if (enableQuotes) {
            var pendingClose: Char? = null
            var pendingStart = -1
            for (i in 0 until text.length) {
                if (roles[i] == ROLE_PAREN || roles[i] == ROLE_HIDDEN) continue
                val c = text[i]
                val close = pendingClose
                if (close == null) {
                    QUOTE_PAIRS[c]?.let {
                        pendingClose = it
                        pendingStart = i
                    }
                } else if (c == close) {
                    // 只高亮已经闭合的引号，避免流式输出时或孤立的引号把后面整段都染色
                    for (k in pendingStart..i) {
                        if (roles[k] == ROLE_NORMAL) {
                            roles[k] = ROLE_QUOTE
                        }
                    }
                    pendingClose = null
                    pendingStart = -1
                }
            }
        }

        return roles
    }

    private fun append(nodes: List<MarkdownInline>, allowFormatting: Boolean) =
        nodes.fastForEach { append(it, allowFormatting) }

    private fun append(node: MarkdownInline, allowFormatting: Boolean) {
        when (node) {
            is MarkdownInline.Text -> appendText(node.text, allowFormatting)
            is MarkdownInline.Styled -> {
                val childAllowFormatting = allowFormatting && node.style.color == Color.Unspecified
                builder.withStyle(node.style) { append(node.children, childAllowFormatting) }
            }
            is MarkdownInline.Code -> builder.withStyle(context.theme.inlineCode) { append(node.code) }
            is MarkdownInline.Link -> appendLink(node)
            is MarkdownInline.Math -> appendMath(node)
            // 图片在分段时已经被拆出去单独渲染，走到这里只剩替代文本可用
            is MarkdownInline.Image -> builder.append(node.alt.orEmpty())
        }
    }

    private fun appendText(text: String, allowFormatting: Boolean) {
        val roles = flatRoles
        if (!allowFormatting || roles == null || text.isEmpty()) {
            builder.append(text)
            return
        }
        val base = flatCursor
        flatCursor += text.length
        val quoteStyle = context.theme.quotedText
        val parenStyle = context.theme.parenthesisText

        var i = 0
        while (i < text.length) {
            val role = roles[base + i]
            var j = i + 1
            while (j < text.length && roles[base + j] == role) {
                j++
            }
            val chunk = text.substring(i, j)
            when (role) {
                ROLE_HIDDEN -> Unit
                ROLE_QUOTE -> {
                    if (quoteStyle != null) {
                        builder.withStyle(quoteStyle) { append(chunk) }
                    } else {
                        builder.append(chunk)
                    }
                }
                ROLE_PAREN -> {
                    if (parenStyle != null) {
                        builder.withStyle(parenStyle) { append(chunk) }
                    } else {
                        builder.append(chunk)
                    }
                }
                else -> builder.append(chunk)
            }
            i = j
        }
    }

    private fun appendLink(link: MarkdownInline.Link) {
        val widget = context.components.inlineLink?.invoke(scope, link)
        if (widget != null) {
            appendWidget(widget, alternateText = link.children.plainText())
            return
        }
        val annotation = LinkAnnotation.Url(
            url = link.destination,
            styles = TextLinkStyles(style = context.theme.link),
            linkInteractionListener = context.linkListener,
        )
        builder.withLink(annotation) { append(link.children, allowFormatting = false) }
    }

    private fun appendMath(math: MarkdownInline.Math) {
        val widgets = context.components.inlineMath?.invoke(scope, math.formula).orEmpty()
        if (widgets.isEmpty()) {
            builder.withStyle(MATH_SOURCE_STYLE) { append(math.formula) }
            return
        }
        hasMathWidget = true
        widgets.forEachIndexed { index, widget ->
            // 零宽空格给拆开的公式提供换行点
            if (index > 0) builder.append('​')
            appendWidget(widget, alternateText = math.formula)
        }
    }

    private fun appendWidget(widget: MarkdownInlineWidget, alternateText: String) {
        val id = "markdown:${inlineContent.size}"
        inlineContent[id] = widget.toInlineTextContent()
        // alternateText 不允许为空
        builder.appendInlineContent(id, alternateText.ifEmpty { "\uFFFD" })
    }
}
