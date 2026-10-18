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

1. **当前**：以 RikkaHub 3.0.0 为基线，接上 SillyTavern 角色卡、世界书、预设、正则和作者备注。粘性与冷却保存在内存里，按会话和用户轮计算。
2. **缓存友好的提示词组装**：稳定的分层顺序；用分块摘要代替逐条滑动窗口；深度注入锚定在固定消息上；宏结果冻结；世界书粘性/冷却写入持久化存储；显式缓存断点；每轮缓存命中统计和前缀差异。
3. **更多酒馆体验**：沙箱 WebView 里的 HTML 前端卡、群聊、快捷回复。

## English

PocketTavern-Lite is a lightweight, Android-first roleplay chat app. It tracks RikkaHub 3.0.0 and adds SillyTavern character cards (V2/V3 PNG and JSON, including alternate greetings), lorebooks, presets (`prompts` + `prompt_order`), regex scripts, and author's notes. Heavy unrelated forks features are intentionally absent. The app id is `io.github.delightedmaster.pockettavern`, Firebase is optional, and the license is AGPL-3.0. Build with `git submodule update --init`, `pnpm install` inside `web-ui`, then `./gradlew :app:assembleDebug`. Step 2 is cache-friendly prompt assembly; step 3 is sandboxed HTML cards, group chat, and quick replies.

## 致谢 / Acknowledgements

- [RikkaHub](https://github.com/rikkahub/rikkahub) 及其维护者 re-ovo 与贡献者。本仓库保留 RikkaHub 3.0.0 的 git 历史与上游版权声明，并在此基础上做选择性修改。
- [MiaoWuNYA/rikkahub-sillytavern-android](https://github.com/MiaoWuNYA/rikkahub-sillytavern-android)。SillyTavern 兼容层（宏、世界书、角色卡、预设、正则、作者备注）从该项目移植，并改写到 RikkaHub 3.0.0 的 `ChatRequestFactory` 生成管线上。该分支里的情侣空间、生活中心、机器人、插件沙箱、通话、设备工具和 Chaquopy Python 没有带入。
- [SillyTavern](https://github.com/SillyTavern/SillyTavern)。角色卡、世界书、预设和正则脚本的字段与行为对齐其公开格式。

三者均为 AGPL-3.0。分发本项目的修改版本时，请遵守 AGPL-3.0，并保留上述版权声明。
