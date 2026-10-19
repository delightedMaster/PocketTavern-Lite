# Changelog

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
