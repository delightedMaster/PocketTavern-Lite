# PocketTavern-Lite

PocketTavern-Lite 是一个面向 Android 的轻量角色扮演聊天应用，界面和交互沿用 SillyTavern 的习惯：角色卡、世界书、预设和正则脚本可以直接拿来用。它从 [RikkaHub](https://github.com/rikkahub/rikkahub) 3.0.0 出发，只保留聊天本身需要的部分，目标是装得下、开得快，并且让模型请求的前缀尽量稳定，从而提高提示词缓存命中率。

协议：AGPL-3.0。上游版权声明保留。

## 目标

- **轻量**：不带情侣空间、生活中心、QQ/微信机器人、主动消息、插件沙箱、语音/视频通话、设备工具或 Python 运行时。
- **Android 优先**：Kotlin + Jetpack Compose，安装包与 RikkaHub 可以并存。
- **高缓存命中**：同一轮对话里，世界书注入和宏的取值按用户轮冻结，避免工具循环每一步都改写前缀。
- **SillyTavern 兼容**：角色卡 V1/V2/V3（PNG 与 JSON，含多开场白）、世界书（粘性、冷却、分组、递归、预算、扫描深度、插入位置）、作者备注、预设（`prompts` + `prompt_order`）和正则脚本（仅显示 / 仅提示词 / 深度范围）。

应用显示名是 **PocketTavern-Lite**，`applicationId` 为 `io.github.delightedmaster.pockettavern`。Firebase 默认关闭，没有 `app/google-services.json` 也能编译；需要遥测时加上 `-Prikkahub.enableFirebase=true`。

## 构建

需要 JDK 21、Android SDK（含 CMake 3.22.1 与 NDK）以及 Node.js 与 pnpm。

```bash
git submodule update --init
cd web-ui && pnpm install && cd ..
./gradlew :app:assembleDebug
```

调试包的 applicationId 带 `.debug` 后缀。与上游同步的方法见 [docs/UPSTREAM.md](docs/UPSTREAM.md)。

## 路线

1. **已完成**：以 RikkaHub 3.0.0 为基线，接上 SillyTavern 角色卡、世界书、预设、正则和作者备注。
2. **当前**：缓存友好的提示词组装。顺序是系统/预设、角色卡、本会话的记忆世界书、可见历史，然后是动态尾部（关键词触发的世界书、状态、作者备注、时间）。触发的条目不会放到角色卡前面。每 40 层总结更早的消息，只把最近 10 层继续发给模型；两个数字都可以改。摘要追加到记忆世界书，分隔是 `---` 加楼层范围，旧摘要不会被改写。记忆太长时，在同一次总结里把更早的块收成一条带版本号的回顾。默认用当前聊天模型，摘要请求复用聊天前缀；也可以换一个连接，那时不带主预设。总结前可以先审阅修改。已总结的消息默认归档，不进提示词，界面里隐藏，可以一键恢复；也可以改成直接删除。聊天菜单里有「立即总结」。Claude 在记忆世界书末尾和历史末尾打缓存断点。世界书粘性/冷却、冻结后的宏和前缀差异写在会话里。
3. **更多酒馆体验**：沙箱 WebView 里的 HTML 前端卡、群聊、快捷回复。

## English

PocketTavern-Lite is a lightweight, Android-first roleplay chat app. It tracks RikkaHub 3.0.0 and adds SillyTavern character cards (V2/V3 PNG and JSON, including alternate greetings), lorebooks, presets (`prompts` + `prompt_order`), regex scripts, and author's notes. Heavy unrelated forks features are intentionally absent. The app id is `io.github.delightedmaster.pockettavern`, Firebase is optional, and the license is AGPL-3.0. Build with `git submodule update --init`, `pnpm install` inside `web-ui`, then `./gradlew :app:assembleDebug`.

Prompt order is the system prompt, the character card, this chat's memory lorebook, the visible history, then a dynamic tail (keyword-triggered lore, state, author's note, and time). Triggered entries are not placed before the character card. Every 40 floors the older messages are summarized and only the latest 10 stay visible; both numbers are settings. The summary is appended to the memory lorebook with a `---` floor-range separator and is never rewritten. When that entry grows past a token threshold, the same turn folds the older blocks into a versioned recap. The default summary model is the chat model, so the summary request reuses the chat prefix. A separate connection profile runs without the main preset. There is a manual "summarize now" action and an optional review before the summary is saved. Summarized messages are archived by default (hidden, omitted from the prompt, restored in one tap) or deleted when that setting is on. Claude marks a cache breakpoint at the end of the memory lorebook and another at the end of history. Sticky, cooldown, frozen macros, and the prefix diff live on the conversation. Step 3 is sandboxed HTML cards, group chat, and quick replies.

## 致谢 / Acknowledgements

- [RikkaHub](https://github.com/rikkahub/rikkahub) 及其维护者 re-ovo 与贡献者。本仓库保留 RikkaHub 3.0.0 的 git 历史与上游版权声明，并在此基础上做选择性修改。
- [MiaoWuNYA/rikkahub-sillytavern-android](https://github.com/MiaoWuNYA/rikkahub-sillytavern-android)。SillyTavern 兼容层（宏、世界书、角色卡、预设、正则、作者备注）从该项目移植，并改写到 RikkaHub 3.0.0 的 `ChatRequestFactory` 生成管线上。该分支里的情侣空间、生活中心、机器人、插件沙箱、通话、设备工具和 Chaquopy Python 没有带入。
- [SillyTavern](https://github.com/SillyTavern/SillyTavern)。角色卡、世界书、预设和正则脚本的字段与行为对齐其公开格式。
- [Summaryception](https://github.com/Lodactio/Extension-Summaryception)（AGPL-3.0）。内置的中英文总结提示词改编自它的 narrative 预设：只写已有摘要里还没有的新情节，输出一行。没有使用 EphemeralAlien/AutoSummarizer 的提示词。

三者均为 AGPL-3.0。分发本项目的修改版本时，请遵守 AGPL-3.0，并保留上述版权声明。
