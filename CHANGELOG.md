# Changelog

## Unreleased

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
