package me.rerere.rikkahub.data.ai.tools.local

import me.rerere.ai.core.Tool

class LocalTools {
    val javascriptTool by lazy { buildJavascriptTool() }

    val timeTool by lazy { buildTimeInfoTool() }

    val askUserTool by lazy { buildAskUserTool() }

    val chartDisplayTool by lazy { buildChartDisplayTool() }

    fun getTools(options: List<LocalToolOption>): List<Tool> {
        val tools = mutableListOf<Tool>()
        if (options.contains(LocalToolOption.JavascriptEngine)) {
            tools.add(javascriptTool)
        }
        if (options.contains(LocalToolOption.TimeInfo)) {
            tools.add(timeTool)
        }
        // LocalToolOption.Tts stays serializable so old assistants still decode. Do not build a TTS tool.
        if (options.contains(LocalToolOption.AskUser)) {
            tools.add(askUserTool)
        }
        if (options.contains(LocalToolOption.ChartDisplay)) {
            tools.add(chartDisplayTool)
        }
        return tools
    }
}
