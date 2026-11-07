# PocketTavern-Lite

<p align="center">
  <b>简体中文</b> | <a href="README_EN.md">English</a>
</p>

<p align="center">
  <a href="https://github.com/delightedMaster/PocketTavern-Lite/releases/latest"><img src="https://img.shields.io/github/v/release/delightedMaster/PocketTavern-Lite?label=%E6%9C%80%E6%96%B0%E7%89%88%E6%9C%AC&sort=semver" alt="最新版本"></a>
  <br>
  <a href="https://github.com/delightedMaster/PocketTavern-Lite/releases/latest"><b>⬇️ 前往 Releases 下载最新 APK</b></a>
</p>

> 安装包怎么选：大多数手机下载 `arm64-v8a`；雷电、MuMu 等电脑模拟器下载 `x86_64`；不确定就下载 `universal`（体积较大，但通用）。

PocketTavern-Lite 是一款面向 Android 平台的轻量、高效角色扮演（RP）聊天客户端。项目基于 [RikkaHub](https://github.com/rikkahub/rikkahub) 3.0.0 构建，深度对齐 [SillyTavern](https://github.com/SillyTavern/SillyTavern)（酒馆）核心生态资产与交互习惯，专注于纯粹、流畅的移动端沉浸式对话体验。

开源协议：[AGPL-3.0](LICENSE)。保留所有上游版权声明与开源授权。

---

## 核心特性

- 🚀 **轻量专注与纯原生架构**：使用现代 Kotlin + Jetpack Compose 打造，聚焦角色扮演核心交互，启动迅速、内存占用极低，可与官方 RikkaHub 共存运行。
- 🎭 **全面对齐 SillyTavern 生态**：
  - **角色卡**：完整支持 V1、V2 及 V3 规范（PNG 嵌入与 JSON），支持多开场白、角色专属配置与备选问候语；新对话开场白自动替换 `{{user}}`、`{{char}}` 等占位符，开场白变量宏（`{{setvar}}`、`{{getvar}}`、`{{addvar}}`、`{{incvar}}`）按会话独立保存。
  - **预设编排**：按酒馆 Prompt Manager 的方式组装提示词——条目开关、`prompt_order` 顺序、角色描述 / 世界书 / 聊天记录等占位位置、相对与绝对（按深度插入）位置、末尾 assistant 预填充和 `squash_system_messages` 均与酒馆一致，支持作者备注（Author's Note）深度与频率控制；支持在设置页独立管理预设，并在聊天扩展选择器与助手配置中将世界书与预设分开展示和直接绑定。
  - **世界书（Lorebook）**：支持关键词匹配、递归扫描、插入深度、Token 预算控制与动态条目追加。
  - **正则脚本（Regex）**：支持显示层（Display）、提示词层（Prompt）及双层同时生效的正则处理；内置**在线正则脚本编辑器**，支持直接编写、调试并在模型原文上即时预览替换效果，支持助手级消息正则一键清空。
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
  - 内置 20+ 提供商预设及灵活的自定义端点，支持 OpenAI Chat Completions / Responses 协议、Anthropic Messages 协议、Google Gemini `generateContent` / `Interactions`，并支持在 Gemini 预设内一键开启 Google Vertex AI（支持 Express API Key 及服务账号 JSON 认证）。
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

> 当上述 4 项完整配置时，`./gradlew :app:assembleRelease` 将生成已签名的 Release APK；若未提供则生成未签名安装包。推送 `v*` 语义化标签至 GitHub 或在 GitHub Actions 中手动触发 Release 工作流时，自动化流水线将自动创建版本标签、编译并发布 GitHub Release 资产。

---

## 开发路线

- **0.1.0**：以 RikkaHub 3.0.0 为基础，完成 SillyTavern 核心资产（角色卡、世界书、预设、正则与作者备注）深度集成。
- **0.1.1**：清理冗余模块，确立轻量角色扮演定位，完善基于 GitHub Releases 的更新检查。
- **0.1.2**：实现导入兼容性诊断报告，重构模型提供商管理与连通性测试工具。
- **0.1.3**：支持聊天内 HTML 代码块渲染，引入上下文与输出 Token 精确预算管理，支持预设与正则一并导入。
- **0.1.4**：实现正文后原始 HTML 原位渲染及整页代码块识别，放行常用前端 CDN、字体与图床安全白名单。
- **0.1.5**：
  - 升级应用内自动更新引擎（支持代理、加速镜像、状态栏进度通知及下载后自动唤起安装）。
  - 新增酒馆正则脚本在线编辑与即时预览调试弹窗（RegexScriptEditor）。
  - 优化 CI/CD 构建流水线，自动规范构建产物命名（版本号 + ABI 标识）并生成 SHA-256 校验文件。
  - 全面重写中英文双语技术文档。
- **0.1.6**：
  - 世界书、正则、预设移到设置页单独管理；提示词页只保留提示词注入和作者备注。
  - 发布工作流支持在 Actions 里手动触发，自动创建版本 tag。
- **0.1.7**：
  - 修复导入的酒馆预设、正则美化在聊天中不生效的问题；新对话的开场白正确替换 `{{user}}`、`{{char}}` 等占位符，开场白变量宏按对话独立保存。
  - 聊天扩展选择器分开显示世界书和预设，支持将预设直接绑定到助手。
  - 助手消息正则列表新增「删除全部」按钮，确认后一键清空当前助手的所有正则。
  - 移除重复的独立「Google Vertex AI」内置服务商，统一通过「Gemini」预设中的 Vertex AI 开关启用（已填密钥或模型的旧配置自动保留为自定义服务商）。
  - 补齐多语言文案，修复 Lint 与若干稳定性问题。
- **0.1.8**：
  - 助手详情页「消息正则规则」接入公共 `RegexScriptEditor` 弹窗，支持即时样本替换预览与语法校验，列表优化为紧凑单行等宽展示。
  - 正则修饰符语法校验增强：统一运行时与编辑器的 `/pattern/flags` 解析，非法修饰符给出即时明确报错，避免静默失效。
  - 正则管理与联动清理：设置页正则分组支持手动删除并带二次确认；删除预设时自动同步清理其附带的正则分组。
  - 对话与预设双向同步：已有对话预设始终跟随助手自身的绑定实时生效，在聊天扩展选择器中勾选预设直接持久化同步至助手。
  - 导入与更新体验优化：酒馆预设导入优先使用 JSON 内部元数据名称与描述；应用内检查更新阶段增加国内镜像源兜底；扩展选择器空状态自适应滚动防截断。
- **0.1.9**：
  - 酒馆预设按 Prompt Manager 原样组装：条目开关只看 `prompt_order`、相对 / 绝对位置语义修正、占位条目原位展开、聊天记录之后的条目与 assistant 预填充可用。Izumi 等复杂预设的格式要求能完整送达模型，选项卡片等美化正则无需改写即可命中。**已导入的预设需要重新导入一次。**
  - 正则深度与酒馆一致（0 = 最新一条）；修复变长 lookbehind 正则输出重复内容的问题。
  - 前端卡片中的选项按钮可将文字填入输入框（兼容酒馆 `#send_textarea` / `#send_but`），新增 `/setinput`。
  - 助手详情页新增「世界书 / 正则 / 预设」独立入口，与全局设置对齐；扩展管理只保留快捷消息、提示词注入和技能。
  - Gemini 不再丢弃第一条之后的 system 消息。
- **0.1.10**：
  - 修复 0.1.9 使用酒馆预设时 Vertex AI / Gemini、DeepSeek 思考模式报错：预设末尾的 assistant 预填充只在 Claude 未开启思考时保留，其余情况按 user 发送。
  - 兼容 Izumi 1029 新版选项卡片：酒馆助手接口新增 `getCurrentMessageId()` / `getLastMessageId()`，`message_id` 改为楼层号。
  - 导入预设时点名不会运行的酒馆助手脚本。
- **0.1.11**（当前版本）：
  - 长回复可以完整往下看：HTML 卡片不再卡在 720dp；超长正文和代码块分段排版，流式输出时也不再裁切。
  - 没有自带颜色的输出文字跟随当前深浅色主题；正则 / HTML 写明的颜色和代码高亮保持原样。

---

## 致谢与上游开源项目

- [RikkaHub](https://github.com/rikkahub/rikkahub)：优秀的原生 Android AI 对话客户端基线。本项目保留 RikkaHub 的 Git 历史与上游版权声明。
- [MiaoWuNYA/rikkahub-sillytavern-android](https://github.com/MiaoWuNYA/rikkahub-sillytavern-android)：提供了将酒馆核心逻辑接入 Android 端的宝贵设计经验与实现参考。
- [SillyTavern](https://github.com/SillyTavern/SillyTavern)：强大的角色扮演与前端交互生态标准制定者。
- [Summaryception](https://github.com/Lodactio/Extension-Summaryception)（AGPL-3.0）：内置总结提示词基于其 narrative 叙事预设进行深度优化与适配。

分发本项目的修改版本时，请遵守 [AGPL-3.0](LICENSE) 许可协议，并保留上述版权与致谢声明。
