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

## 功能

- SillyTavern 角色卡 V1/V2/V3（PNG 与 JSON，含多开场白）、世界书、预设（`prompts` + `prompt_order`）、正则（仅显示 / 仅提示词 / 深度范围）和作者备注。
- 缓存友好的提示词顺序：系统/预设、角色卡、本会话的记忆世界书、可见历史，然后是动态尾部（关键词触发的世界书、变量、作者备注、时间、当前发言者）。触发的条目不会放到角色卡前面。
- 记忆与总结。默认每 40 层总结更早的楼层，只把最近 10 层继续发给模型。两个数字都在「提示词 → 作者备注」里，标签是「达到多少楼层时总结」和「保留楼层」。摘要追加到这个会话自己的记忆世界书，分隔是 `---` 加楼层范围，旧摘要不会被改写。记忆超过 token 阈值（默认 2000）时，同一次总结把更早的块收成一条带版本号的回顾。默认用当前聊天模型，这样摘要请求能复用聊天前缀；换一个连接时不带主预设。可以先审阅再保存。已总结的消息默认归档（不进提示词，界面隐藏，一键恢复），也可以改成直接删除。聊天菜单里有「立即总结」。
- HTML 前端卡、快捷回复和群聊，见下面三节。
- Claude 在记忆世界书末尾和历史末尾打缓存断点。OpenAI 使用 `prompt_cache_key`，DeepSeek 使用会话 `user`。Gemini 的 `cachedContents` 接口没有接。

## HTML 前端卡与酒馆助手

消息在显示层正则之后，如果含有 `html`、`body`、`div`、`style`、`script`、`iframe`、`table`、`section` 或 `canvas` 标签，就在单独的 WebView 里渲染。单独的 `<br>` 仍按 Markdown 显示。流式生成过程中先显示 Markdown，生成结束后再挂上卡片。

每条消息一个 WebView。JavaScript 只能调用注入的 `PocketTavern` 接口，看不到 API key，也读不到应用存储。DOM storage 关闭。默认拦截所有网络请求；在「HTML 卡片域名」里写上域名后，该域名及其子域名可以加载。高度由卡片上报，限制在 32–720 dp。

变量写在动态尾部，不进系统提示词或角色卡。尾部只放全局变量、本聊天变量，以及最新一条消息自己的变量。

支持的酒馆助手（JS-Slash-Runner）调用：

| 调用 | 行为 |
| --- | --- |
| `getChatMessages(range)` | 读已存储的消息文本。`range` 可省略、`"all"`、数字（`-1` 是最后一条，`0` 是第一条）、`"start-end"` 或 `{start, end}` |
| `getVariables({type})` | `chat`（默认）、`global` 或 `message`（这张卡片所在的消息） |
| `setVariables` / `replaceVariables` / `insertOrAssignVariables` | 按 key 合并，不会清掉表里的其他变量 |
| `triggerSlash(command)` | 走下面列出的基础斜杠命令 |
| `Mvu.getMvuData({type})` / `Mvu.replaceMvuData(data, {type})` | `chat` 或 `message`。JSON 存在变量 `mvu.stat_data` |

没有实现：`generateRaw`、`eventEmit`、`SillyTavern.getContext`、iframe 消息，以及完整的 STscript。

## 快捷回复

扩展页可以管理快捷回复，也可以导入 SillyTavern 的 JSON（v2 的 `qrList`、v1 的 `quickReplies`，或直接一个数组）。角色卡上的绑定仍然有效，聊天里还可以再绑定一组，按钮出现在输入框上方。隐藏的按钮不显示。

只执行这些斜杠命令，命令之间用换行或 `|` 分开。没有斜杠的一行会作为用户消息发送。

- `/send text` 发送并生成回复
- `/trigger name` 按标题运行另一条快捷回复，最多几层
- `/setvar key value` 或 `/setvar key=value` 写入本聊天变量
- `/getvar key` 把值放进输入框
- `/gen` 在最后一条是用户消息时生成；`/gen text` 先发送这段文字
- `/input prompt` 询问一行，并把回答放进输入框。它不会挡住后面的命令

## 群聊

扩展页里新建群聊，选择成员和发言顺序：自然、列表、随机、手动。打开后是一条普通会话，成员共用这本记忆世界书。

成员卡按名单顺序放在稳定前缀里，禁言的成员也留在前缀中，这样关掉某个人不会把缓存打乱。当前发言者的名字只出现在动态尾部。整个群使用同一个模型。自然顺序不另外调用模型：用户刚发的消息里只提到一名成员时由那个人接话，否则按列表。手动顺序在输入框上方点选。

## 签名

发布签名不放进仓库。四个值都有时才给 release 包签名，少一个就打未签名的 release 包：

| `local.properties`（相对 app 模块） | 环境变量 |
| --- | --- |
| `storeFile` | `ANDROID_KEYSTORE_FILE` |
| `storePassword` | `ANDROID_KEYSTORE_PASSWORD` |
| `keyAlias` | `ANDROID_KEY_ALIAS` |
| `keyPassword` | `ANDROID_KEY_PASSWORD` |

推送 `v*` tag 时，`.github/workflows/release.yml` 会构建 release APK 并挂到 GitHub Release。仓库里如果有下面四个 secret，就用它们签名；否则上传未签名包：

- `ANDROID_KEYSTORE_BASE64`（keystore 文件的 base64）
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

## 路线

1. **已完成**：以 RikkaHub 3.0.0 为基线，接上 SillyTavern 角色卡、世界书、预设、正则和作者备注。
2. **已完成**：缓存友好的提示词组装和追加式记忆总结。
3. **已完成**：沙箱 WebView 里的 HTML 前端卡、群聊、快捷回复，以及 tag 发布时的签名流程。

## English

PocketTavern-Lite is a lightweight, Android-first roleplay chat app. It tracks RikkaHub 3.0.0 and adds SillyTavern character cards (V2/V3 PNG and JSON, including alternate greetings), lorebooks, presets (`prompts` + `prompt_order`), regex scripts, author's notes, sandboxed HTML front-end cards, quick replies, and basic group chats. Heavy unrelated forks features are intentionally absent. The app id is `io.github.delightedmaster.pockettavern`, version 0.1.0, Firebase is optional, and the license is AGPL-3.0. Build with `git submodule update --init`, `pnpm install` inside `web-ui`, then `./gradlew :app:assembleDebug`.

Prompt order is the system prompt, the character card, this chat's memory lorebook, the visible history, then a dynamic tail (keyword-triggered lore, variables, author's note, time, and the current speaker). Triggered entries are not placed before the character card. Every 40 floors the older messages are summarized and only the latest 10 stay visible. Both numbers are on the prompt page under author's note: "Summarize at floors" and "Floors to keep". The summary is appended to this chat's memory lorebook with a `---` floor-range separator and is never rewritten. When that entry grows past the token threshold (default 2000), the same turn folds the older blocks into a versioned recap. The default summary model is the chat model, so the summary request reuses the chat prefix. A separate connection profile runs without the main preset. There is a manual "summarize now" action and an optional review before the summary is saved. Summarized messages are archived by default (hidden, omitted from the prompt, restored in one tap) or deleted when that setting is on.

HTML cards render after the visual regex, one sandboxed WebView per message. Network is blocked unless the host is listed under "HTML card hosts". The card cannot see API keys or app storage. Supported 酒馆助手 calls are `getChatMessages`, `getVariables`, `setVariables` / `replaceVariables` / `insertOrAssignVariables` (merge), `triggerSlash`, and `Mvu.getMvuData` / `Mvu.replaceMvuData` (stored as `mvu.stat_data`). `generateRaw`, `eventEmit`, `SillyTavern.getContext`, iframe messaging, and full STscript are absent. Variables stay in the dynamic tail.

Quick replies import SillyTavern JSON, bind to a character or to the chat, and show as buttons above the input. The slash commands are `/send`, `/trigger`, `/setvar`, `/getvar`, `/gen`, and `/input`.

Group chats use natural, list, pooled, or manual speaker order. Member cards stay in member order in the stable prefix, including muted members. The speaker name is a tail line. One model serves the whole group, and the memory lorebook is the conversation's. Natural order matches a uniquely mentioned name; it does not call the model.

Release signing reads `storeFile`, `storePassword`, `keyAlias`, and `keyPassword` from `local.properties` (paths relative to the app module) or `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. A tag push of `v*` builds the release APK. GitHub secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` sign it when all four are present; otherwise the attached APK is unsigned.

## 致谢 / Acknowledgements

- [RikkaHub](https://github.com/rikkahub/rikkahub) 及其维护者 re-ovo 与贡献者。本仓库保留 RikkaHub 3.0.0 的 git 历史与上游版权声明，并在此基础上做选择性修改。
- [MiaoWuNYA/rikkahub-sillytavern-android](https://github.com/MiaoWuNYA/rikkahub-sillytavern-android)。SillyTavern 兼容层（宏、世界书、角色卡、预设、正则、作者备注）从该项目移植，并改写到 RikkaHub 3.0.0 的 `ChatRequestFactory` 生成管线上。该分支里的情侣空间、生活中心、机器人、插件沙箱、通话、设备工具和 Chaquopy Python 没有带入。
- [SillyTavern](https://github.com/SillyTavern/SillyTavern)。角色卡、世界书、预设和正则脚本的字段与行为对齐其公开格式。
- [Summaryception](https://github.com/Lodactio/Extension-Summaryception)（AGPL-3.0）。内置的中英文总结提示词改编自它的 narrative 预设：只写已有摘要里还没有的新情节，输出一行。没有使用 EphemeralAlien/AutoSummarizer 的提示词。

三者均为 AGPL-3.0。分发本项目的修改版本时，请遵守 AGPL-3.0，并保留上述版权声明。
