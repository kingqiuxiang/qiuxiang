# AI YAPI Test Console

一个零外部依赖的本地 AI 接口协作控制台，用于读取 YAPI/OpenAPI 参数、扫描项目代码、生成一键填充参数，并在接口写完后联动前端页面和 API 发起测试。

## 能力概览

- 精美流畅的 Web 控制台：玻璃拟态卡片、动画反馈、接口列表、参数预览、测试结果面板。
- YAPI/OpenAPI 导入：支持填写本地 JSON 文件路径、HTTP URL，或直接粘贴 JSON 内容。
- 一键参数填充：优先使用 YAPI/OpenAPI 的 `example/mock/schema`，再结合字段名、类型和项目模型推断。
- AI 增强：配置 `AI_API_KEY` 后可调用 OpenAI 兼容接口，让参数更贴近业务语义。
- 项目代码扫描：识别 Spring Mapping、Node Router、fetch/axios 调用和 DTO/类型字段。
- 快捷启动：根据仓库结构推断启动命令，也可以输入 dev 命令并从控制台启动项目。
- 自动测试：可访问开发环境前端页面，并用生成的参数调用选中接口。

## 快速开始

```bash
javac Main.java
java Main
```

打开：

```text
http://localhost:8787
```

默认示例接口文件：

```text
examples/yapi-sample.json
```

## AI 配置

默认使用 OpenAI 兼容的 Chat Completions 协议：

```bash
export AI_API_KEY="your-api-key"
export AI_MODEL="gpt-4o-mini"
# 可选：自定义 OpenAI 兼容服务地址
export AI_BASE_URL="https://api.openai.com/v1/chat/completions"

javac Main.java
java Main
```

未配置 `AI_API_KEY` 时，系统会自动使用本地确定性规则生成参数。

## 使用流程

1. 在「输入源」里填写 YAPI/OpenAPI JSON 路径、URL，或粘贴 JSON。
2. 点击「分析项目与接口」，系统会同时读取接口参数并扫描项目代码。
3. 在接口列表选择目标接口。
4. 点击「一键生成参数」，必要时勾选「使用 AI 增强」。
5. 在「前端/API 测试」里填写前端页面 URL 和 API Base URL。
6. 点击「运行测试」，系统会请求前端页面，并调用当前接口输出结果。

## API 端点

控制台本身也提供 JSON API，便于集成到其他工具：

- `GET /api/scan?path=/path/to/project`
- `POST /api/analyze`
- `POST /api/fill`
- `POST /api/test`
- `POST /api/quick-start`
- `GET /api/quick-start/logs?id=<session-id>`

## 说明

这是一个可直接运行的本地开发原型，刻意保持单文件 Java 实现和零依赖，方便在任意项目中复制、扩展或接入现有平台。
