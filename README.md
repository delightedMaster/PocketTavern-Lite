# PocketTavern-Lite

<p align="center">
  <b>简体中文</b> | <a href="README_EN.md">English</a>
</p>

PocketTavern-Lite 是一款面向 Android 平台的轻量、高效角色扮演（RP）聊天客户端。项目基于 [RikkaHub](https://github.com/rikkahub/rikkahub) 3.0.0 构建，深度对齐 [SillyTavern](https://github.com/SillyTavern/SillyTavern)（酒馆）核心生态资产与交互习惯，专注于纯粹、流畅的移动端沉浸式对话体验。

开源协议：[AGPL-3.0](LICENSE)。保留所有上游版权声明与开源授权。

---

## 核心特性

- 🚀 **轻量专注与纯原生架构**：使用现代 Kotlin + Jetpack Compose 打造，聚焦角色扮演核心交互，启动迅速、内存占用极低，可与官方 RikkaHub 共存运行。
- 🎭 **全面对齐 SillyTavern 生态**：
  - **角色卡**：完整支持 V1、V2 及 V3 规范（PNG 嵌入与 JSON），支持多开场白、角色专属配置与备选问候语。
  - **预设编排**：支持 `prompts` 与 `prompt_order` 精确编排，支持作者备注（Author's Note）深度与频率控制。
  - **世界书（Lorebook）**：支持关键词匹配、递归扫描、插入深度、Token 预算控制与动态条目追加。
  - **正则脚本（Regex）**：支持显示层（Display）与提示词层（Prompt）正则处理；内置**在线正则脚本编辑器**，支持直接编写、调试并在模型原文上即时预览替换效果。
- 🎨 **原位 HTML 与前端卡片渲染**：
  - **正文原位渲染**：正文后另起一行的原始 HTML（状态栏、好感度、属性面板等）直接在消息流中原位渲染为交互卡片，紧邻的 `<style>` 与内容自动合并，阅读自然流畅。
  - **整页代码块渲染**：支持酒馆助手卡片规范，代码块包含完整网页结构（`<body>...</body>` 或 `<html>...</html>`）时直接渲染为卡片，兼容 `text`、`xml`、`html` 等常见标记。
  - **沙箱安全与网络放行**：独立 WebView 沙箱隔离，禁用敏感本地存储并屏蔽 API Key 访问；默认放行常用脚本 CDN（jsdelivr、unpkg、cdnjs、jQuery、Tailwind）、字体库（Google Fonts 及其国内镜像）和图床（catbox、imgur），同时支持用户自定义域名白名单。
  - **酒馆助手 JS API 基础兼容**：支持 `getChatMessages`、`getVariables`、`setVariables`、`triggerSlash` 及 `Mvu`（`mvu.stat_data`）等状态栏与属性变量交互。
- ⚡ **高缓存命中的提示词编排架构**：
  - 请求上下文划分为**稳定前缀**（系统预设、角色卡设定、记忆世界书、可见历史）与**动态尾部**（触发词条、动态变量、作者备注、当前时间与发言者）。
  - 动态宏与变量按用户轮次冻结，避免工具调用导致前缀变动，最大化利用 Anthropic（Claude 缓存断点）、OpenAI（`prompt_cache_key`）与 DeepSeek 等模型的 Prompt Caching 机制，大幅缩短生成首字延迟并降低 API 成本。
- 🧠 **渐进式长对话记忆总结**：
  - 支持按楼层自动触发记忆压缩与提炼（默认达 40 层时总结更早消息，保留最近 10 层活跃上下文）。
  - 结构化摘要以 `---` 楼层范围分隔符自动追加沉淀至该会话专属的记忆世界书中，历史旧摘要永久保留。当记忆条目超出预算阈值时，自动归纳为版本化回顾。
  - 支持一键手动即时总结与保存前审阅；已总结的消息默认一键归档（隐藏且不占 Token，可随时完整还原）。
- 🌐 **广泛的模型提供商支持与独立诊断**：
  - 内置 20+ 提供商预设及灵活的自定义端点，支持 OpenAI Chat Completions / Responses 协议、Anthropic Messages 协议、Google Gemini `generateContent` / `Interactions` 以及 Google Vertex AI（支持 Express 及服务账号 JSON 认证）。
  - 内置提供商连通性测试与消息收发实时诊断工具。
- 📲 **应用内无缝自动更新与安装**：
  - 内置基于 OkHttpClient 的独立下载器，支持应用全局网络代理设置与国内加速镜像兜底（`gh-proxy.com`）。
  - 拥有专属通知渠道，显示实时下载进度与百分比；下载完成后通过 FileProvider 自动唤起系统安装器。
  - 支持设备 ABI 架构（`arm64-v8a` / `x86_64`）智能识别与最优安装包推荐。
- 👥 **多人群聊与快捷回复**：
  - **多人群聊**：支持自然轮换（根据用户提及的角色自动应答）、列表顺序、随机池与手动点选发言；群成员共享记忆世界书，成员卡稳定留在前缀中以保护缓存。
  - **快捷回复（Quick Replies）**：支持导入 SillyTavern 快捷回复配置，并在输入框上方以操作栏常驻展示，支持 `/send`、`/trigger`、`/setvar`、`/getvar`、`/gen`、`/input` 等基础宏指令。

---

## 快速上手与构建

### 基础要求
- **构建环境**：JDK 21、Android SDK（平台 API 34+）
- **设备要求**：Android 8.0（API Level 26）及以上，推荐 64 位设备（`arm64-v8a` 或 `x86_64`）

### 编译源码
```bash
# 克隆仓库并拉取子模块
git clone https://github.com/delightedMaster/PocketTavern-Lite.git
cd PocketTavern-Lite
git submodule update --init

# 构建 Debug APK
./gradlew :app:assembleDebug
```
构建产物输出路径为 `app/build/outputs/apk/debug/app-debug.apk`。

---

## 签名与发布配置

若需要构建正式签名的 Release 版本，可通过在 `local.properties`（位于项目根目录，路径相对于 app 模块）配置密钥信息，或设置相应的环境变量：

| `local.properties` 配置项 | 环境变量 | 说明 |
| --- | --- | --- |
| `storeFile` | `ANDROID_KEYSTORE_FILE` | 签名密钥文件路径（如 `release.keystore`） |
| `storePassword` | `ANDROID_KEYSTORE_PASSWORD` | 密钥库密码 |
| `keyAlias` | `ANDROID_KEY_ALIAS` | 密钥别名 |
| `keyPassword` | `ANDROID_KEY_PASSWORD` | 密钥密码 |

> 当上述 4 项完整配置时，`./gradlew :app:assembleRelease` 将生成已签名的 Release APK；若未提供则生成未签名安装包。推送 `v*` 语义化标签至 GitHub 时，自动化流水线将自动编译并发布 GitHub Release 资产。

---

## 开发路线

- **0.1.0**：以 RikkaHub 3.0.0 为基础，完成 SillyTavern 核心资产（角色卡、世界书、预设、正则与作者备注）深度集成。
- **0.1.1**：清理冗余模块，确立轻量角色扮演定位，完善基于 GitHub Releases 的更新检查。
- **0.1.2**：实现导入兼容性诊断报告，重构模型提供商管理与连通性测试工具。
- **0.1.3**：支持聊天内 HTML 代码块渲染，引入上下文与输出 Token 精确预算管理，支持预设与正则一并导入。
- **0.1.4**：实现正文后原始 HTML 原位渲染及整页代码块识别，放行常用前端 CDN、字体与图床安全白名单。
- **0.1.5**（当前版本）：
  - 升级应用内自动更新引擎（支持代理、加速镜像、状态栏进度通知及下载后自动唤起安装）。
  - 新增酒馆正则脚本在线编辑与即时预览调试弹窗（RegexScriptEditor）。
  - 优化 CI/CD 构建流水线，自动规范构建产物命名（版本号 + ABI 标识）并生成 SHA-256 校验文件。
  - 全面重写中英文双语技术文档。

---

## 致谢与上游开源项目

- [RikkaHub](https://github.com/rikkahub/rikkahub)：优秀的原生 Android AI 对话客户端基线。本项目保留 RikkaHub 的 Git 历史与上游版权声明。
- [MiaoWuNYA/rikkahub-sillytavern-android](https://github.com/MiaoWuNYA/rikkahub-sillytavern-android)：提供了将酒馆核心逻辑接入 Android 端的宝贵设计经验与实现参考。
- [SillyTavern](https://github.com/SillyTavern/SillyTavern)：强大的角色扮演与前端交互生态标准制定者。
- [Summaryception](https://github.com/Lodactio/Extension-Summaryception)（AGPL-3.0）：内置总结提示词基于其 narrative 叙事预设进行深度优化与适配。

分发本项目的修改版本时，请遵守 [AGPL-3.0](LICENSE) 许可协议，并保留上述版权与致谢声明。
