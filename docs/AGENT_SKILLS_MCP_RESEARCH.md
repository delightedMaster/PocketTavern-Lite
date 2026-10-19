# 酒馆场景下 Skills、Agent 与 MCP 的取舍

调研日期：2026-10-19。目标：Android 原生、角色聊天和同人写作优先。调研采用 Exa 两个方向的定向搜索（共返回 10 项），并阅读下列一手文档及项目 README。资料能够证明具体用途与实现边界，不能证明这些功能在全部酒馆用户中的使用率。

## 结论

| 能力 | 与目标直接相关的用途 | 成本 / 限制 | 本轮处理 |
| --- | --- | --- | --- |
| Skills | 按需读取文风、角色口吻、关系推进、世界设定参考、改稿检查清单 | 工具调用模型适配、读取预算；不能把“技能”直接等同于运行脚本 | 保留现有 `use_skill`；优先发展只读写作资料包 |
| Agent | 资料检索 → 拟稿 → 设定核对 → 修订；长篇同人文比即时聊天更适合 | 多轮 API 成本、延迟、运行状态管理；与普通角色对话应独立开关 | 保留现有工具调用循环，按轻量化要求移除工作区及终端；不扩大为多 Agent 系统，也不把它认定为完整 TauriTavern Agent |
| MCP | 接专用作品资料库、百科检索、外部记忆库等已有服务 | 远程服务配置、模型工具调用兼容、工具输出长度；本地 stdio 还需额外运行时 | 保留 MCP 客户端与现有授权；不因移除浏览器 Web 服务而删掉 MCP 或联网搜索 |

以上是产品取舍建议，不代表本轮新增了写作 Agent、资料库或新的工具权限。

## 一手资料与实际区别

1. [SillyTavern Function Calling](https://docs.sillytavern.app/for-contributors/function-calling/)：官方列出的用途包括外部 API / 搜索、记忆读取与写入、计算和掷骰。是否调用工具取决于模型和 API，默认有工具循环上限。这证明工具机制本身有用，但并不意味着每一轮普通聊天都需要启用 Agent。
2. [TauriTavern Agent Overview](https://tauritavern.github.io/en/agent/)：这是酒馆分支中的专门创作路径，支持受约束的工作区、运行时间线和多步骤修订；文档把快速普通生成与多步骤写作明确分开。不能据此推断所有 SillyTavern 安装都具有这一系统。
3. [TauriTavern SKILLS](https://tauritavern.github.io/en/agent/skills.html)：明确把技能定义为按需读取的本地知识包。适合文风、人物关系节奏、状态栏格式和设定资料；不自动执行包内 scripts，也不授予 MCP 或工具权限。这与轻量安卓写作的目标较吻合。
4. [SillyTavern MCP Client](https://github.com/bmen25124/SillyTavern-MCP-Client)：存在实际第三方实现，要求搭配服务端插件，并启用 function calling；README 展示 stdio、SSE、Streamable HTTP。它证明 MCP 扩展可用，不能据此把 MCP 称为 SillyTavern 核心默认功能。
5. [davidgibbons/sillytavern-skills](https://github.com/davidgibbons/sillytavern-skills)：这里的 Skills 供 Codex 制作角色卡和开发扩展，不能误当成 SillyTavern 聊天中原生运行的技能。
6. [foreverse-app/character-card-skills](https://github.com/foreverse-app/character-card-skills)：提供角色卡创作、聊天问题诊断等具体写作用途，也可由外部 Coding Agent 使用。项目自述的效果指标未经本次独立验证，不以其宣传数据决定取舍。

## PocketTavern 当前实现

- `SkillsTools.kt` 的 `use_skill` 读取启用技能的 SKILL.md 或包内文本，并约束路径；它本身不执行包内脚本。
- `ChatToolFactory.kt` 统一装配搜索、记忆、Skills 和 MCP 工具。
- 后续瘦身分支已按用户要求移除工作区、PRoot 与 Termux 依赖。只读 Skills 与 MCP 不依赖该运行时，继续保留；内置技能改为在聊天中生成草稿，不声称写入文件或执行脚本。
- 搜索已有独立外部搜索工具和模型内建搜索两条路径。可以查作品设定、人物背景及历史资料；不保证搜索内容正确，也不保证每个模型一定主动调用。

## 已确认实施的裁剪

移除 AI 日历、屏幕使用时间和 AI 剪贴板工具及权限；正常复制粘贴保留。移除内置浏览器聊天服务器、前端工程和构建 Node/pnpm 要求。移除通用文档解析模块及文档附件入口；角色卡 PNG/JSON、世界书、预设、正则、快捷回复、Skills 的文本资源导入保留；后续按用户要求一并移除聊天图片附件、草图和 OCR。

这些裁剪与是否保留 Skills / Agent / MCP 是两组独立决策。

## PR #4 当时的本地验证范围

- `:app:compileDebugKotlin` 通过。
- `:app:testDebugUnitTest` 的导入报告 / 预设、旧配置、设置持久化、搜索和 Skills 回归检查共 49 项通过，0 失败。
- Manifest、工作流 YAML / shell 语法、已移除模块的引用与版本号检查通过。当时验证的版本为 0.1.1 / 194；本轮更新后版本为 0.1.2 / 195。
- 本地通过临时 Gradle init 脚本跳过未改动的工作区 C++ 构建，仅验证 Kotlin 和 JVM 测试；该临时脚本不属于项目改动。该次本地验证未构建完整 APK；后续提交已推送至 PR #4，由 GitHub Actions 构建。
