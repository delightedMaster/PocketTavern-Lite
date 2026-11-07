package me.rerere.ui.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse

/**
 * 正文默认色。流式、流式刚结束、重新打开历史走的是同一条规则，不因为生成结束而改色。
 *
 * 样式或内容色已经指定时（气泡的 contentColor、正则 / HTML 写在段落上的颜色）原样保留。
 * 两边都没指定时用主题色，而不是内容色缺省的纯黑。
 */
fun resolveMessageTextColor(styleColor: Color, contentColor: Color, themeColor: Color): Color =
    styleColor.takeOrElse { contentColor }.takeOrElse { themeColor }
