# 模型提供商与连接测试

PocketTavern-Lite 的 Android 客户端预置 22 个提供商配置，并允许添加任意自定义地址、API Key、请求头和模型。提供商名称与协议类型分开：兼容 OpenAI 的第三方服务不需要再写一套适配器。预设地址并不保证所有模型都支持所有可选功能，以实际发送测试结果为准。

| 协议 | 设置入口 | 普通 / 流式消息 |
| --- | --- | --- |
| OpenAI Chat Completions | OpenAI 类型，关闭 Responses API | 支持 |
| OpenAI Responses | OpenAI 类型，开启 Responses API | 支持 |
| Anthropic Messages | Claude 类型 | 支持 |
| Gemini generateContent | Google 类型，关闭 Interactions API | 支持 |
| Gemini Interactions (Beta) | Google 类型，开启 Interactions API | 支持 |
| Vertex AI generateContent | Google 类型，开启 Vertex AI | 支持；支持 API Key 或服务账号 |

Vertex AI 开启时使用 generateContent；Interactions 仅用于 Gemini Developer API。Gemini 的 OpenAI 兼容服务也可创建为 OpenAI 类型，填写对应的兼容地址。

## Android 测试入口

设置 → 模型提供商 → 选择提供商 → 配置 → 测试连接。

- 测试使用当前输入的配置，无需先保存。
- “测试连接 / 读取模型”调用该提供商的模型列表接口。列表成功仅代表这个接口响应正常；有些中转或账号不允许列模型，失败仍可手填模型 ID 发送消息。
- “发送测试消息”使用可编辑消息及选定模型，流式开关决定调用哪个生成方法。每次只执行选中的测试，不会自动同时发送三组请求。
- 模型级自定义请求头和请求体沿用已配置值；手填的新模型仅在测试窗口中使用。测试不会创建聊天记录或修改提供商配置。
- 显示当前协议、回复全文和耗时；错误可点开详情。90 秒超时，关闭窗口取消请求；空文本回复会明确报错。
- “单独测试工具调用”是可选检查，仅检查模型是否返回指定调用，不执行工具。未返回调用不会显示成功，也不代表普通聊天不可用。
- 发送消息和工具测试会产生提供商正常 API 用量。

## 新预设的地址依据

- Anthropic：`https://api.anthropic.com/v1`（已有原生 Messages 适配器）
- Mistral：<https://docs.mistral.ai/api>
- Groq：官方 SDK 的 [默认地址](https://github.com/groq/groq-python/blob/main/src/groq/_client.py) 与 [Chat Completions 路径](https://github.com/groq/groq-python/blob/main/src/groq/resources/chat/completions.py)
- Cerebras：<https://inference-docs.cerebras.ai/introduction>

本次使用模拟提供商验证诊断行为，并运行已有协议流回放测试；没有用户 API Key，未对各家的真实账号进行联网生成验证。


## 独立 Vertex 与新增订阅接口

- 默认列表中的 `Gemini` 使用 Gemini Developer API；`Google Vertex AI` 使用 Google Cloud Vertex AI。它与 Vercel AI Gateway 是不同服务。
- Vertex 明确提供 `Express`（API Key）与 `JSON 登录`（服务账号 JSON）两种方式，沿用原有 Location 与请求逻辑。导入 JSON 会验证类型及必需字段，避免把无效文件与旧凭据混在一起。
- `OpenCode Go`：`https://opencode.ai/zen/go/v1`。按官方文档为不同模型使用 Chat Completions、Responses 或 Messages；发送真实客户端 `User-Agent: PocketTavern-Lite` 和每段会话稳定的 `x-opencode-session`。连接测试窗口也有自己的会话 ID。官方定位为编程代理服务，适用范围和额度由服务方决定。来源：<https://opencode.ai/docs/go/>。
- `Command Code GOAT`：`https://api.commandcode.ai/provider/v1`。Claude 使用 Messages，其余模型使用 Chat Completions 或手动开启的 Responses；Messages 请求按其文档发送 Bearer 鉴权。来源：<https://commandcode.ai/docs/plans/goat>、<https://commandcode.ai/docs/provider>。
- 五个被移除的默认预设不会再补回；未配置的旧预设会清理，已有密钥、模型或自定义地址的连接转为普通自定义配置。推荐入口及其广告数据已删除。

新增预设与路由按官方文档及公开模型列表核对；没有用户 API Key，尚未验证真实订阅的生成权限。
