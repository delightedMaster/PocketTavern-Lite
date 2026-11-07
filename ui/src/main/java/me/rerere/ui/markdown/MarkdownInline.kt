package me.rerere.ui.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
                val shown = chunk.withoutTrailingBreak(drop = index < chunks.lastIndex)
                LayoutText(shown, rendered.inlineContent, textStyle, contentColor)
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
) {
    Text(
        text = text,
        modifier = modifier,
        inlineContent = inlineContent,
        // 段落默认色跟随主题。Span 上的颜色（正则 / HTML / 链接）会盖过它；两边都没指定时不另填黑或白。
        color = contentColor,
        style = style,
    )
}

/** 切在换行上时，换行符留在上一截末尾。后面还有内容时去掉它，由下一段顶上来，避免多空一行。 */
private fun AnnotatedString.withoutTrailingBreak(drop: Boolean) =
    if (drop && text.endsWith('\n')) subSequence(0, length - 1) else this

private class RenderedInlineText(
    val text: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>,
    val hasMathWidget: Boolean,
)

private val MATH_SOURCE_STYLE = SpanStyle(fontFamily = FontFamily.Monospace)

// 开引号 -> 闭引号。单引号常被当作撇号（don't、it’s）使用，不参与配对
private val QUOTE_PAIRS = mapOf('"' to '"', '“' to '”', '「' to '」', '『' to '』')

private class InlineTextBuilder(
    private val context: MarkdownContext,
    private val scope: MarkdownInlineScope,
) {
    private val builder = AnnotatedString.Builder()
    private val inlineContent = mutableMapOf<String, InlineTextContent>()
    private var hasMathWidget = false

    // 还没闭合的引号：等待的闭引号，以及开引号在输出文本里的位置。引号可以跨越加粗、链接等节点
    private var pendingQuoteClose: Char? = null
    private var pendingQuoteStart = 0

    fun build(content: List<MarkdownInline>): RenderedInlineText {
        append(content)
        return RenderedInlineText(builder.toAnnotatedString(), inlineContent, hasMathWidget)
    }

    private fun append(nodes: List<MarkdownInline>) = nodes.fastForEach { append(it) }

    private fun append(node: MarkdownInline) {
        when (node) {
            is MarkdownInline.Text -> appendText(node.text)
            is MarkdownInline.Styled -> builder.withStyle(node.style) { append(node.children) }
            is MarkdownInline.Code -> builder.withStyle(context.theme.inlineCode) { append(node.code) }
            is MarkdownInline.Link -> appendLink(node)
            is MarkdownInline.Math -> appendMath(node)
            // 图片在分段时已经被拆出去单独渲染，走到这里只剩替代文本可用
            is MarkdownInline.Image -> builder.append(node.alt.orEmpty())
        }
    }

    private fun appendText(text: String) {
        val offset = builder.length
        builder.append(text)
        val quoteStyle = context.theme.quotedText ?: return
        text.forEachIndexed { index, char ->
            val close = pendingQuoteClose
            if (close == null) {
                QUOTE_PAIRS[char]?.let {
                    pendingQuoteClose = it
                    pendingQuoteStart = offset + index
                }
            } else if (char == close) {
                // 只高亮已经闭合的引号，避免流式输出时或孤立的引号把后面整段都染色
                builder.addStyle(quoteStyle, pendingQuoteStart, offset + index + 1)
                pendingQuoteClose = null
            }
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
        builder.withLink(annotation) { append(link.children) }
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
        builder.appendInlineContent(id, alternateText.ifEmpty { "�" })
    }
}
