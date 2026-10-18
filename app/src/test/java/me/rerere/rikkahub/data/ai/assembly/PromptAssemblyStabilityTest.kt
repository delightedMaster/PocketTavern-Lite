package me.rerere.rikkahub.data.ai.assembly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptAssemblyStabilityTest {

    @Test
    fun `one hundred turns keep an append-only prefix except on summary turns`() {
        val history = mutableListOf<HistoryMessage>()
        var state = PromptCacheState()
        var clock = 1_000
        val outputs = mutableListOf<AssemblyOutput>()
        val ratios = mutableListOf<Double>()
        val breakTurns = mutableListOf<Int>()
        val policy = FloorPolicy(triggerFloors = 40, keepFloors = 10)
        var newestSummary = ""

        repeat(120) { turn ->
            history += HistoryMessage(
                id = "u$turn",
                role = "user",
                text = "user $turn mentions dragon {{time}} {{random}} " + "lore ".repeat(20),
            )
            val plan = planFloorSummary(history, policy, state.summarizedIds)
            val cacheBreak = plan.dropIds.isNotEmpty()
            if (cacheBreak) {
                val summary = "summary:" + plan.dropIds.first() + "-" + plan.dropIds.last()
                newestSummary = summary
                state = commitFloorSummary(state, history, plan, summary)
                if (memoryOverThreshold(state.memoryBlocks, tokenThreshold = 20)) {
                    val recap = consolidateMemory(state.memoryBlocks, "recap:" + state.memoryBlocks.size)
                    if (recap != null) {
                        assertEquals(newestSummary, recap.last { it.kind == "summary" }.text)
                        state = state.copy(memoryBlocks = recap)
                    }
                }
                breakTurns += turn
            }
            val triggered = if (turn % 7 == 0) {
                listOf(LoreTrigger(id = "dragon", text = "A dragon watches the road.", depth = 3))
            } else {
                emptyList()
            }
            val output = assemblePrompt(
                AssemblyRequest(
                    staticLayers = listOf(
                        "You are the innkeeper.",
                        "Description: the inn sits on the cliff.",
                    ),
                    history = history,
                    triggeredLore = triggered,
                    authorNote = "Stay in character. Beat $turn.",
                    timeText = "time-bucket-${clock / 5}",
                    memoryText = "memory note $turn",
                    toolText = "tool:search",
                    stateVars = "affection=${turn / 10}",
                    state = state,
                    render = { message ->
                        message.text
                            .replace("{{time}}", "t$clock")
                            .replace("{{random}}", "r-${message.id}")
                    },
                ),
            )
            val memoryAt = output.pieces.indexOfFirst { it.cacheBreakpoint == "memory" }
            val historyAt = output.pieces.indexOfFirst { it.layer == PromptLayer.HISTORY }
            if (state.memoryBlocks.isNotEmpty()) {
                assertTrue(memoryAt >= 0)
                assertTrue(memoryAt < historyAt)
                assertTrue(output.pieces[memoryAt].text.contains("---\n["))
            }
            if (triggered.isNotEmpty()) {
                val loreAt = output.pieces.indexOfFirst { it.text == "A dragon watches the road." }
                val lastHistory = output.pieces.indexOfLast { it.layer == PromptLayer.HISTORY }
                assertTrue(loreAt > lastHistory)
                assertEquals(PromptLayer.TAIL, output.pieces[loreAt].layer)
                assertTrue(output.stablePrefix.none { it == "A dragon watches the road." })
            }
            val characterAt = output.pieces.indexOfFirst { it.text.startsWith("Description:") }
            val loreBeforeCard = output.pieces.indexOfFirst { it.text == "A dragon watches the road." }
            if (characterAt >= 0 && loreBeforeCard >= 0) {
                assertTrue(loreBeforeCard > characterAt)
            }
            if (outputs.isNotEmpty()) {
                ratios += sharedPrefixRatio(outputs.last().fullText, output.fullText)
                val previous = outputs.last().stablePrefix
                val appendOnly = output.stablePrefix.size >= previous.size &&
                    output.stablePrefix.take(previous.size) == previous
                if (cacheBreak) {
                    assertTrue("summary turn $turn should break the stable prefix", !appendOnly)
                } else {
                    assertTrue("turn $turn moved the stable prefix without a summary", appendOnly)
                }
            }
            outputs += output
            state = output.state
            clock += 7
            history += HistoryMessage(id = "a$turn", role = "assistant", text = "reply $turn " + "word ".repeat(12))
        }

        val average = ratios.average()
        println("PREFIX_STABILITY turns=${outputs.size} avgSharedPrefix=$average cacheBreakTurns=$breakTurns")
        assertTrue(outputs.size >= 100)
        assertTrue(breakTurns.isNotEmpty())
        assertTrue("average shared prefix $average", average >= 0.75)
        assertTrue(state.frozenText.getValue("u119").contains("t${1000 + 119 * 7}"))
        assertTrue(state.frozenText.getValue("u119").contains("r-u119"))
        assertTrue(renderMemoryLore(state.memoryBlocks).contains("---\n["))
        val recap = state.memoryBlocks.first()
        assertEquals("recap", recap.kind)
        assertTrue(recap.version >= 1)
        assertEquals("summary", state.memoryBlocks.last().kind)
        assertEquals(newestSummary, state.memoryBlocks.last().text)
    }

    @Test
    fun `a blank summary does not archive the chunk`() {
        val history = (0 until 40).map { index ->
            HistoryMessage(id = "m$index", role = if (index % 2 == 0) "user" else "assistant", text = "m$index")
        }
        val plan = planFloorSummary(history, FloorPolicy(triggerFloors = 40, keepFloors = 10), emptySet())
        assertEquals(30, plan.dropIds.size)
        val state = commitFloorSummary(PromptCacheState(), history, plan, summary = null)
        assertTrue(state.summarizedIds.isEmpty())
        assertTrue(state.memoryBlocks.isEmpty())
    }

    @Test
    fun `manual summarize keeps the newest floors`() {
        val history = (0 until 15).map { index ->
            HistoryMessage(id = "m$index", role = "user", text = "m$index")
        }
        val plan = planFloorSummary(
            history,
            FloorPolicy(triggerFloors = 40, keepFloors = 10, force = true),
            emptySet(),
        )
        assertEquals((0 until 5).map { "m$it" }, plan.dropIds)
        val state = commitFloorSummary(PromptCacheState(), history, plan, "summary:0-4")
        assertEquals("summary:0-4", state.memoryBlocks.single().text)
        assertEquals(1, state.memoryBlocks.single().startFloor)
        assertEquals(5, state.memoryBlocks.single().endFloor)
        assertTrue(renderMemoryLore(state.memoryBlocks).startsWith("---\n[1-5]\nsummary:0-4"))
    }
}
