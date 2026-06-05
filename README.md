# AI YAPI Dev Assistant

这是一个零依赖的本地 AI 接口研发工作台。它可以读取 YAPI 接口参数、扫描当前项目代码，并基于二者生成可测试的请求参数；同时提供项目快捷启动、前端页面检查和接口调用测试能力。

## 能力

- 精美流畅的单页交互工作台
- 读取 YAPI `interfaceId` 或 `projectId`
- 扫描项目代码中的路由、HTTP 调用和测试框架信号
- AI 一键参数填充
  - 配置 `AI_API_KEY` 或 `OPENAI_API_KEY` 时调用 OpenAI 兼容接口
  - 未配置时自动使用本地启发式规则生成测试参数
- 快捷启动项目命令并查看进程日志
- 访问开发环境前端页面，提取标题、资源、表单等信息
- 使用生成参数直接调用接口并返回状态、耗时、响应体

## 启动

```bash
javac Main.java
java Main
```

默认访问：

```text
http://localhost:7070
```

可通过环境变量修改端口：

```bash
PORT=8088 java Main
```

## AI 配置

默认使用 OpenAI 兼容的 Chat Completions 协议：

```bash
export AI_API_KEY=你的密钥
export AI_MODEL=gpt-4o-mini
export AI_API_BASE_URL=https://api.openai.com/v1/chat/completions
java Main
```

也兼容：

- `OPENAI_API_KEY`
- `OPENAI_MODEL`
- `OPENAI_API_BASE_URL`

如果没有配置密钥，系统会使用本地规则完成参数填充，仍然可以完成基础测试流程。

## 使用流程

1. 打开工作台，填写 YAPI 地址和接口 ID，点击「读取 YAPI」。
2. 点击「扫描项目」，系统会读取当前仓库代码，识别接口和测试相关上下文。
3. 点击「AI 一键填参」，生成 headers、query、pathParams、body 和断言建议。
4. 如需启动被测项目，在「快捷启动项目」中输入启动命令。
5. 填写前端页面 URL，点击「检查页面」。
6. 填写接口 Base URL，点击「调用接口测试」。

也可以点击「一键执行推荐流程」，让系统按已填写的信息串联执行。

## API

- `GET /api/health`：服务状态
- `POST /api/yapi/import`：读取 YAPI
- `POST /api/project/scan`：扫描项目
- `POST /api/ai/fill`：AI 或本地规则生成请求参数
- `POST /api/project/start`：启动项目命令
- `GET /api/project/processes`：查看进程列表
- `GET /api/project/logs?id=xxx`：查看进程日志
- `POST /api/project/stop`：停止进程
- `POST /api/test/page`：检查前端页面
- `POST /api/test/request`：调用接口测试

## 注意

项目启动命令会在本机仓库目录内执行，请只输入可信命令。YAPI token 会在后端返回的请求 URL 中脱敏展示。
