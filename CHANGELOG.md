# Changelog

## Unreleased

## 0.1.11

更新内容:

- **长回复不再被裁掉一截**：HTML 卡片以前最高只有 720dp，正则美化或状态栏把整段回复包进卡片时，超出的正文不在布局里，外层列表又抢走了滑动。现在卡片按内容增高，只在超过单个视图的安全高度后才在卡片内部滚动。普通 Markdown（长中文、多段、超长单段）和代码块拆成多段排版，避免一个文本布局高到画不出来；流式输出不再用会裁切内容的尺寸动画。已保存的消息原文不变。
- **输出文字跟随当前主题**：这不是有意把生成结束画成黑色。流式过程中正文走主题色；生成结束只是打开文本选择，并在这时才把完整 HTML 卡片交给 WebView。选择层没有自己的文字色，WebView 以前也没写主题色，缺省就落到纯黑，所以流式是白的、刚结束变黑。重新打开历史时选择从第一帧就在，Markdown 仍用主题色，所以又是白的。现在这三种状态共用同一套颜色：没写颜色的正文和卡片用主题色，正则 / HTML 写明的颜色和代码高亮保留。

Updates:

- **Long replies stay visible**: HTML cards were capped at 720dp, so a regex-beautified reply or status block taller than that was outside the layout and the chat list took the scroll. Cards now grow with their content and scroll inside only past a safe view height. Markdown (long Chinese, many paragraphs, one huge paragraph) and code blocks are split into several layouts so one text layout cannot grow past what can be drawn. Streaming no longer clips the body with a size animation. Saved message text is unchanged.
- **Reply text follows the theme**: turning the finished reply black was not intentional. While streaming, the body uses the theme color; when generation ends the code only enables text selection and, at that moment, hands a finished HTML card to a WebView. Selection does not set a text color, and the WebView previously set none either, so the default is pure black: white while streaming, black the moment it finishes. Reloading history composes selection from the first frame and Markdown keeps the theme color, so it is white again. All three states now share one rule: text and cards without their own color use the theme; explicit regex / HTML colors and syntax highlighting stay.

## 0.1.10

更新内容:

- **修复 0.1.9 使用酒馆预设时 Vertex AI / Gemini、DeepSeek 思考模式报错**：预设末尾的 assistant 条目（如 Izumi 的「卡思维链」）在 0.1.9 中作为预填充发送，推理模型会拒绝请求（Vertex：`requests ending with a model turn are not supported`；DeepSeek：`reasoning_content in the thinking mode must be passed back to the API`）。现在只有 Claude 未开启思考时保留预填充，其余情况按 user 发送，指令内容不变；聊天历史不受影响。
- **兼容 Izumi 1029 新版选项卡片**：酒馆助手接口新增 `getCurrentMessageId()` / `getLastMessageId()`，`getChatMessages` 返回的 `message_id` 改为楼层号（与酒馆助手一致）。新版卡片在运行时读取所在消息原文解析选项，0.1.9 会提示「需要酒馆助手的前端渲染与当前消息接口」。
- **导入报告点名不会运行的酒馆助手脚本**：预设内嵌的酒馆助手脚本（如「泉此方悬浮窗」）不会执行，导入时明确列出。

Updates:

- **Fixed Vertex AI / Gemini and DeepSeek thinking-mode errors with SillyTavern presets in 0.1.9**: a preset's trailing assistant entry (e.g. Izumi's 卡思维链) went out as a prefill, which reasoning models reject (Vertex: `requests ending with a model turn are not supported`; DeepSeek: `reasoning_content in the thinking mode must be passed back to the API`). The prefill is now kept only for Claude without extended thinking and is otherwise sent as a user turn with the same text; chat history is unchanged.
- **Izumi 1029 option panel**: added `getCurrentMessageId()` / `getLastMessageId()` to the 酒馆助手 bridge; `message_id` from `getChatMessages` is now the floor index, as in 酒馆助手. The new panel reads its own message at runtime; 0.1.9 showed "需要酒馆助手的前端渲染与当前消息接口".
- **Import report names tavern_helper scripts that will not run** (e.g. 「泉此方悬浮窗」).

## 0.1.9

更新内容:

- **酒馆预设按 Prompt Manager 原样组装**：导入时保留 `prompt_order` 顺序、占位条目（chatHistory、charDescription 等）和条目语义；条目开关只看 `prompt_order`（以前 `prompts[].enabled` 也参与判断，Izumi 47 条启用条目中有 19 条被误关）；`injection_position` 0 按顺序原地排列、1 才按深度插入聊天记录（以前语义相反）；聊天记录之后的条目跟在完整历史后面，末尾 assistant 预填充生效；支持 `squash_system_messages`。复杂预设的格式要求能完整送达模型，选项卡片等美化正则无需改写即可命中。**已导入的预设需要重新导入一次才会使用新的组装方式。**
- **正则深度与酒馆一致**：0 = 最新一条消息，未设置 = 不限制（以前 `maxDepth: 0` 被当成不限制，且整体偏移一层）；修复变长 lookbehind 正则把整段原文重复输出的问题。
- **前端卡片选项可填入输入框**：卡片运行环境提供酒馆的 `#send_textarea` / `#send_but`，点击选项后文字进入输入框；新增 `/setinput` 命令。
- **助手设置与全局设置对齐**：助手详情页新增「世界书 / 正则 / 预设」独立入口（显示当前绑定），助手消息正则移到「正则」页；扩展管理只保留快捷消息、提示词注入和技能。
- **其它**：`{{lastUserMessage}}` / `{{lastCharMessage}}` 不再把预设条目当成聊天消息；Gemini 不再丢弃第一条之后的 system 消息；同位置世界书条目保持原有顺序；README 顶部新增 Release 下载入口。

Updates:

- **SillyTavern presets assembled like the Prompt Manager**: import keeps `prompt_order`, marker entries and entry semantics; toggles come from `prompt_order` only (previously `prompts[].enabled` also counted, turning off 19 of Izumi's 47 active entries); `injection_position` 0 stays in order and 1 is inserted into chat history by depth (previously reversed); entries after chat history follow the whole history so an assistant prefill works; `squash_system_messages` is supported. **Re-import existing presets once to use the new assembly.**
- **Regex depth matches SillyTavern**: 0 = newest message, unset = unbounded (previously `maxDepth: 0` meant unbounded and every depth was off by one); fixed variable-length lookbehind regexes duplicating the input.
- **Card options fill the input box**: cards get SillyTavern's `#send_textarea` / `#send_but`; added `/setinput`.
- **Assistant settings match global settings**: separate Lorebooks / Regex / Presets entries on the assistant page (showing what is bound); assistant message regexes moved to the Regex page; Extensions keeps quick messages, prompt injections and skills.
- **Other**: `{{lastUserMessage}}` / `{{lastCharMessage}}` ignore preset entries; Gemini keeps system messages after the first; lorebook entries in one slot keep their order; README now links to the latest release at the top.

## 0.1.8

更新内容:

- **助手消息正则接入 RegexScriptEditor**：助手详情页「消息正则规则」接入公共正则编辑弹窗，支持即时样本替换预览与实时语法校验；列表采用紧凑单行等宽展示。
- **正则修饰符校验与语法错误提示**：解析器统一支持 `/pattern/flags` 格式，遇到未知修饰符给出即时明确报错（如 `无效的正则修饰符: z`），杜绝因底层异常被吞导致的静默失效。
- **正则分组删除与自动清理**：设置页「正则」分组支持手动删除并带二次确认；「设置 → 预设」删除预设时，自动联动清理其附带的正则分组。
- **已有对话预设跟随助手双向同步**：已有对话中的预设始终跟随助手自身的绑定实时生效，解决了已有对话修改不同步的割裂问题；在聊天扩展选择器中勾选预设直接持久化同步至助手。
- **酒馆预设导入与元数据支持**：导入酒馆预设时优先读取 JSON 根节点内置的 `name` 和 `description` 属性，缺失时优雅回退。
- **应用内检查更新镜像兜底**：在检测 GitHub 最新版本元数据阶段增加 `gh-proxy.com` 镜像兜底，确保国内直连网络环境下也能稳定检查更新。
- **扩展选择器空状态布局自适应**：空状态加入自适应滚动与最小高度约束，小屏及横屏模式下「管理预设 / 世界书」按钮始终完整可见且可点击；规范繁简中文文案。

> ⚠️ **关于 0.1.6 存量双标志正则的说明**：由于 0.1.6 历史版本在本地持久化时将双标志抹除并覆盖保存，无法通过数据迁移无损还原，受影响的历史双标志规则请重新导入预设或角色卡。

Updates:

- **Assistant Message Regex via RegexScriptEditor**: Assistant prompt page now uses the unified RegexScriptEditor dialog for message regex rules, offering instant sample replacement previews and compile validation, with a compact monospace single-line list row.
- **Regex Flag Validation**: Unified `/pattern/flags` parser with explicit compile errors for unknown flags (e.g. `Invalid regex flag: z`), eliminating silent regex failures.
- **Regex Group Deletion & Synchronized Cleanup**: Preset regex groups in Settings can now be deleted manually with confirmation; deleting a preset in Settings automatically cleans up its associated regex group.
- **Preset Synchronization in Started Conversations**: Presets in existing conversations now consistently follow assistant bindings upon next send; toggling presets in the chat extension picker persists back to the assistant.
- **Preset Import Name & Description Priority**: Importing SillyTavern presets now prioritizes top-level `name` and `description` fields from the JSON before falling back to file names.
- **Update Check Mirror Fallback**: Added `gh-proxy.com` mirror fallback during release metadata check to prevent connection failures under restricted networks.
- **Scrollable Extension Empty State**: Refactored extension empty states with vertical scrolling and bounded height constraints, ensuring action buttons remain reachable on compact or landscape layouts; polished Chinese translations.

## 0.1.7

更新内容:

- 消息正则列表新增「删除全部」按钮，确认后一次清空当前助手的所有正则
- 修复导入的酒馆预设、正则美化在聊天中不生效的问题；新对话的开场白会正确替换 {{user}}、{{char}} 等占位符
- 聊天里的扩展选择分开显示世界书和预设，预设也能直接绑定到助手
- 移除重复的「Google Vertex AI」内置服务商；需要 Vertex AI 时在「Gemini」里打开 Vertex AI 开关即可。已填过密钥或模型的旧配置会保留为自定义服务商
- 补齐多语言文案，修复若干稳定性问题

Updates:

- Added a "Delete all" button to the message regex list that clears all of the assistant's regex rules after confirmation
- Fixed imported SillyTavern presets and regex beautification not applying in chat; new conversations now fill in {{user}}, {{char}} and similar placeholders in greetings
- The chat extension picker now shows world books and presets separately, and presets can be attached to the assistant directly
- Removed the duplicate "Google Vertex AI" built-in provider; turn on the Vertex AI switch in "Gemini" instead. Existing setups with a key or models are kept as a custom provider
- Filled in missing translations and fixed several stability issues

## 0.1.6

- 世界书、正则、预设移到设置页（助手与扩展管理之间）单独管理；预设不再和世界书混在一起。提示词页只保留提示词注入和作者备注。
- 发布工作流支持在 Actions 里手动触发，自动创建版本 tag。

## 0.1.5

- **应用内自动更新与下载体验优化**：
  - 移除系统 DownloadManager 依赖，采用基于 OkHttpClient 的应用内下载引擎，并支持遵循全局应用代理。
  - 新增国内加速镜像兜底（`gh-proxy.com`），解决 GitHub 访问受限时的下载问题。
  - 适配下载状态栏通知渠道与实时百分比、大小进度显示。
  - 下载完成后通过 FileProvider 自动唤起系统安装器。
  - 新增设备 ABI 架构智能匹配与优先推荐显示。
- **酒馆拓展与交互增强**：
  - 新增酒馆正则脚本在线编辑弹窗（RegexScriptEditor），支持在线调试规则与即时替换预览模型原文。
- **CI/CD 构建与发布规范化**：
  - Release 与 Nightly 工作流自动为构建出的 APK 产物附加版本号/构建标签及对应 ABI 标识（`PocketTavern-Lite-${TAG}-<abi>.apk`）。
  - 构建产物自动生成 `sha256sums.txt` 校验清单并同步发布至 GitHub Release。
- **文档与国际化重构**：
  - 重写项目 README 并提供完整中英文双语版本（`README.md` 与 `README_EN.md`），支持顶端一键切换。
  - 优化项目定位与行文表达，全面梳理核心特性、提示词缓存架构、前端卡片渲染与记忆机制。

## 0.1.4

- 正文后面另起一行的原始 HTML（状态栏等）直接在消息里原位渲染成卡片，不再需要从菜单另开网页预览；紧挨着的 `<style>` 和内容合成一张卡片。
- HTML 卡片默认放行常用 CDN、字体和图床（jsdelivr、unpkg、cdnjs、jQuery、Tailwind、Google Fonts 及其镜像、catbox、imgur）。原先为空的域名列表升级后换成默认值，自己填过的保留。
- 不论语言标记，闭合代码块里含有完整页面（`<body>…</body>` 或 `<html>…</html>`）时按前端卡渲染，与酒馆助手一致；常见于用 ```text、```xml 包裹状态栏的预设。
- README 修正：聊天附件已移除的描述前后一致，补充 0.1.3 路线和 HTML 渲染规则。

## 0.1.3

- HTML 代码块直接在聊天内渲染，保留混合正文和多个卡片的顺序，仅对闭合代码块启用。
- 移除聊天附件入口、图片粘贴导入和 OCR 设置及执行路径，角色卡图片导入保留。
- 增加上下文/最大回复 token 自定义设置，已有会话独立保存；请求按估算预算移出完整旧轮次，超出固定内容预算时提示调整。

- 预设/世界书条目提供直接启用开关，点击条目打开详细编辑；区分“编辑预设”标题并兼容旧预设数据。
- 预设与附带正则在一次设置更新中共同导入；报告列出正则数量，单条损坏不再丢弃整组规则，同名导入不会覆盖原有正则。

- 移除工作区入口、文件操作与终端工具、PRoot 原生执行模块及 Termux 依赖，保留旧数据库结构兼容升级。
- 补齐提示词、作者备注、正则规则、开场白等简体中文，并增加用途说明；清理设置中的英文提示。

- 默认助手允许删除，删除后不再自动补回；保留至少一个角色，并自动切换被删除的当前角色。

- 修复测试连接弹窗在编辑模型 ID / 测试消息、键盘弹出后被工具栏一起移除的问题。
- 删除未使用的三张横幅；相机扫码改用 ZXing，移除 Quickie、ML Kit 离线扫码模型及其 CameraX 依赖，保留相册二维码识别与中文搜索词典。
- 新增独立的 Google Vertex AI 预设，明确提供 Express 与服务账号 JSON 两种认证方式，沿用现有地区设置和请求逻辑。
- 移除 APIMart、302.AI、随想 AI 网关、MaruCode、AiHubMix 默认预设及推荐入口，新增 OpenCode Go 与 Command Code GOAT。

## 0.1.2

- 补充官方 Anthropic（Claude）、Mistral AI、Groq 和 Cerebras 预设；明确显示 OpenAI / Gemini 当前接口格式。
- 重做连接与消息测试：独立读取模型列表、手填模型 ID、自定义消息、流式切换、可选工具测试、超时与空回复检查，关闭时取消请求。

- 新增角色卡、世界书 / 预设、正则和快捷回复导入兼容性报告，列出跳过项、未实现功能及已识别的行为差异，可复制。
- 移除日历、屏幕使用时间和 AI 剪贴板工具，正常复制粘贴保留。
- 移除内置 Web 服务、网页前端与通用文档解析；联网搜索、Skills、MCP 及现有工作区暂保留。

## 0.1.1

- 更新提示和详情明确显示 PocketTavern-Lite，更新来源继续使用本项目的 GitHub Releases。
- 修复没有 APK 附件时把发布网页当作安装包下载的问题，改为打开发布页面；尚无正式发布时不显示检查失败。
- 移除 Android 和网页端残留的模型收藏列表、收藏按钮、拖拽排序及收藏写入接口。
- 禁用旧备份中残留的图像生成工具、图像输出和 TTS 工具，保留聊天图片输入。
- 清理已移除功能的运行时依赖注册及消息翻译、收藏修改方法。保留旧数据库表及序列化字段，兼容已有数据。
- 媒体创作、媒体和图像生成、AI 翻译、语音服务、消息收藏及收藏页面继续保持移除。

Update checks read this repository's GitHub releases and compare the tag with `versionName`. Notification settings can turn the check off, or pause it. The About page, share text, and update download links point at PocketTavern-Lite. RikkaHub stays credited there and in the README. The GitHub Release body for a `v*` tag is the matching section of this file.

Removed from the app:

- AI translation (the translator page, in-chat translate, and translation settings).
- Image and video generation, including the media-service settings page and the `:mediagen` module. Chat image attachments, for sending pictures to vision models, stay.
- The Favorites page, per-message favorite action, and model favorites in both the Android and browser interfaces.
- Speech services: text-to-speech, speech recognition, the voice-mode input, and the `:speech` module.

Room keeps the old translation, favorites, generated-media, and media-creation tables so an existing database still opens. The app no longer reads or writes them.

## 0.1.0

First PocketTavern-Lite release. The app id is `io.github.delightedmaster.pockettavern`. It starts from RikkaHub 3.0.0 and keeps the chat path.

### SillyTavern cards

- Character cards V1/V2/V3, PNG and JSON, including alternate greetings.
- Lorebooks with sticky, cooldown, groups, recursion, budget, scan depth, and insertion position.
- Presets (`prompts` + `prompt_order`), regex scripts (display only, prompt only, depth range), author's notes, and macros.
- Couple space, life center, bots, proactive messages, the plugin sandbox, voice and video calls, device tools, and the Python runtime are not included.

### Cache-stable prompts and memory

- Prompt order is the system prompt, the character card, this chat's memory lorebook, the visible history, then a dynamic tail. Triggered lore stays in the tail.
- Defaults: summarize every 40 floors and keep the latest 10. Both are settings. There is a manual "summarize now", and an optional review before the summary is saved.
- Summaries are appended. Older text is not rewritten. A long memory entry is folded into a versioned recap on that same turn.
- The chat model is the default summary model, so the summary request reuses the chat prefix. A separate profile runs without the main preset.
- Archived floors are hidden and left out of the prompt, and can be restored. A setting deletes them instead.
- Claude cache breakpoints sit at the end of the memory lorebook and at the end of history. OpenAI gets a `prompt_cache_key`. DeepSeek gets the session id as `user`.

### HTML cards, quick replies, and group chat

- HTML front-end cards render in a sandboxed WebView after the visual regex. Network is off unless a host is allowlisted. Cards cannot read API keys or app storage. Each message has its own WebView, and the height fits the card.
- 酒馆助手 calls that work: `getChatMessages`, `getVariables`, `setVariables`, `replaceVariables`, `insertOrAssignVariables`, `triggerSlash`, `Mvu.getMvuData`, and `Mvu.replaceMvuData`. Variable state stays in the dynamic tail.
- Quick reply sets import from SillyTavern JSON, bind to a character or a chat, and show above the input. Slash commands are `/send`, `/trigger`, `/setvar`, `/getvar`, `/gen`, and `/input`.
- Group chats support natural, list, pooled, and manual speaker order. Member cards stay in a fixed order in the prefix, including muted members. The group shares the chat's memory lorebook and uses one model.

### Release signing

- Release signing is configured from `local.properties` or environment variables. The keystore is not in the repo.
- Pushing a `v*` tag builds the release APK and attaches it to a GitHub Release. The secrets are `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. If they are absent, the APK is unsigned.
