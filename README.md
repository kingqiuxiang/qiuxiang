# qiuxiang

一个面向开发环境的 **AI YAPI Runner**：读取 YAPI 接口参数和项目代码上下文，生成可测试请求参数，并提供项目快捷启动、前端页面辅助访问、接口调用验证的一键闭环。

## 功能

- 精美的 Web 控制台：配置、扫描、接口选择、参数编辑、执行结果时间线。
- YAPI 接入：通过 `baseUrl + projectId + token` 读取接口列表和接口详情。
- AI 一键填参：支持 OpenAI-compatible Chat Completions；未配置 AI 时自动使用本地规则填充。
- 项目代码扫描：提取 Spring/Express 路由、前端 `fetch`、DTO/Request 模型作为 AI 上下文。
- 快捷启动项目：配置本地启动命令后，可从控制台一键启动并查看日志。
- 接口测试：按填充参数调用开发环境后端，输出状态码、耗时、响应和断言。
- 前端辅助测试：配置前端页面地址后，可在控制台预览或新窗口打开开发环境页面。

## 快速开始

```bash
npm start
```

然后打开：

```text
http://localhost:3025
```

当前仓库未配置真实 YAPI 时，系统会自动加载一个演示接口，方便体验完整流程。

## 配置

复制示例配置：

```bash
cp config/ai-yapi-runner.example.json config/ai-yapi-runner.local.json
```

填写本地配置：

```json
{
  "project": {
    "name": "qiuxiang",
    "root": ".",
    "startCommand": "npm run dev",
    "healthUrl": "http://localhost:8080/health",
    "frontendUrl": "http://localhost:5173"
  },
  "yapi": {
    "baseUrl": "https://yapi.example.com",
    "token": "your-project-token",
    "projectId": "123"
  },
  "ai": {
    "enabled": true,
    "endpoint": "https://api.openai.com/v1/chat/completions",
    "apiKey": "your-api-key",
    "model": "gpt-4o-mini"
  },
  "runner": {
    "targetBaseUrl": "http://localhost:8080",
    "timeoutMs": 15000
  }
}
```

也可以使用环境变量：

- `YAPI_BASE_URL`
- `YAPI_TOKEN`
- `YAPI_PROJECT_ID`
- `AI_ENABLED`
- `AI_API_ENDPOINT`
- `AI_API_KEY`
- `AI_MODEL`
- `TARGET_BASE_URL`
- `PROJECT_START_COMMAND`
- `PROJECT_HEALTH_URL`
- `FRONTEND_URL`

> `config/ai-yapi-runner.local.json` 已加入 `.gitignore`，不要提交 token、API key 或内网敏感地址。

## 推荐工作流

1. 打开控制台，填写 YAPI、开发环境后端、前端页面和 AI 配置。
2. 点击 **扫描代码上下文**，让系统读取项目路由、模型和前端调用线索。
3. 点击 **读取接口列表**，选择刚写完或需要验证的接口。
4. 点击 **AI 一键填参**，生成请求头、query、path params 和 body。
5. 如需启动项目，点击 **快捷启动项目**。
6. 点击 **运行接口测试** 或 **一键闭环**，完成调用验证。

## 测试

```bash
npm test
```
