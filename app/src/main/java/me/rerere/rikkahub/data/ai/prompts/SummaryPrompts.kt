package me.rerere.rikkahub.data.ai.prompts

/**
 * Append-only chunk summary prompts.
 *
 * Adapted from the narrative preset in Lodactio/Extension-Summaryception
 * (AGPL-3.0): https://github.com/Lodactio/Extension-Summaryception
 * The original asks for only the elements needed to continue prior context,
 * and excludes events already covered there. That matches an append-only
 * memory block. The system line is Summaryception's default tracker role.
 *
 * Not taken from EphemeralAlien/AutoSummarizer.
 */
const val SUMMARY_SYSTEM_PROMPT_EN =
    "Role: precise narrative-state tracker. Output only the summary line — no preamble, no commentary, no markdown."

const val SUMMARY_SYSTEM_PROMPT_ZH =
    "角色：精确的叙事状态记录员。只输出摘要那一行，不要前言、评论或 Markdown。"

const val SUMMARY_PROMPT_EN = """<player_name>
{{player_name}}
</player_name>

<prior_context>
{{context_str}}
</prior_context>

<passage_in_question>
{{story_txt}}
</passage_in_question>

Summarize only the necessary elements from the passage_in_question to coherently continue the prior_context. Write only new plot developments that are not already in the prior_context. If the passage uses second person, "you" is the player; use the player name instead of "you".

If prior_context is empty, include the establishing details.

Priority: time and location changes, proper names, plot developments, character motivations and relationship changes, unresolved tensions.

Exclude fluff and events already covered in prior_context.

Write in short phrases. The final response must be a single line:"""

const val SUMMARY_PROMPT_ZH = """<player_name>
{{player_name}}
</player_name>

<prior_context>
{{context_str}}
</prior_context>

<passage_in_question>
{{story_txt}}
</passage_in_question>

只从 passage_in_question 中摘出延续 prior_context 所必需的内容。只写 prior_context 里还没有的新情节。如果原文用第二人称，“你”指玩家，摘要里改用玩家的名字。

如果 prior_context 是空的，补上开场时确立的事实。

优先保留：时间与地点变化、专有名词、情节推进、角色动机与关系变化、尚未解决的张力。

去掉空话，以及 prior_context 已经写过的事。

用短句写。最终回复必须是单独的一行："""

const val SUMMARY_CONSOLIDATION_NOTE_EN =
    "Condense the passage into one higher-level recap. Keep names, unresolved tensions, and plot turns. Do not add events that are not in the passage. One line:"

const val SUMMARY_CONSOLIDATION_NOTE_ZH =
    "把这段已有摘要收成一条更高层的回顾。保留名字、未解决的张力和情节转折。不要添加原文没有的事。一行："
