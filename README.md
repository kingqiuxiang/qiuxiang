# AI 接口开发测试控制台

这是一个零外部依赖的本地系统，用于在接口开发完成后快速完成：

- 读取 YAPI 接口参数（支持粘贴 JSON 或填写 YAPI URL）
- 扫描当前项目代码，提取接口、调用和代码摘要
- 接入 OpenAI 兼容 AI，基于 YAPI + 项目代码一键生成测试参数
- 在未配置 AI 时使用本地启发式规则兜底填参
- 调用开发环境接口，并可同时探测前端页面可访问性
- 提供精美、流畅的 Web 控制台交互

## 启动

```bash
javac Main.java AiDevAssistantServer.java
java Main
```

默认访问：

```text
http://localhost:8718
```

如需修改端口：

```bash
AI_DEV_PORT=9000 java Main
# 或
java Main 9000
```

## AI 配置

系统支持 OpenAI 兼容接口。未配置时仍可使用本地规则生成参数。

```bash
export AI_API_KEY="你的 API Key"
export AI_BASE_URL="https://api.openai.com/v1"   # 可选，默认 OpenAI
export AI_MODEL="gpt-4o-mini"                    # 可选
java Main
```

## 使用流程

1. 打开控制台，系统会自动扫描当前项目代码。
2. 在左侧填写 YAPI URL + Token，或直接粘贴 YAPI JSON。
3. 点击「导入 YAPI」生成接口列表。
4. 选择接口并点击「AI 一键填参」。
5. 填写接口 `Base URL`，例如 `http://localhost:8080`。
6. 可选填写前端页面 URL，例如 `http://localhost:5173`。
7. 点击「运行测试」调用接口并探测前端页面。
8. 也可以点击「AI 快捷启动」一次性完成扫描、填参和测试。

## HTTP API

控制台背后也提供可编排的 HTTP API。

### 健康检查

```bash
curl http://localhost:8718/api/health
```

### 扫描项目代码

```bash
curl http://localhost:8718/api/project/scan
```

### 导入 YAPI

```bash
curl -X POST http://localhost:8718/api/yapi/import \
  -H 'content-type: application/json' \
  -d '{
    "yapiText": {
      "data": {
        "title": "用户详情",
        "path": "/api/user/detail",
        "method": "GET",
        "req_query": [
          {"name": "userId", "type": "number", "required": "1", "example": "1001"}
        ]
      }
    }
  }'
```

### AI 一键填参

```bash
curl -X POST http://localhost:8718/api/ai/fill \
  -H 'content-type: application/json' \
  -d '{
    "endpoint": {
      "method": "GET",
      "path": "/api/user/detail",
      "title": "用户详情",
      "params": [
        {"name": "userId", "type": "number", "required": true}
      ]
    }
  }'
```

### 运行接口和页面联测

```bash
curl -X POST http://localhost:8718/api/test/run \
  -H 'content-type: application/json' \
  -d '{
    "method": "GET",
    "url": "http://localhost:8080/api/user/detail",
    "params": {"userId": 1001},
    "frontendUrl": "http://localhost:5173"
  }'
```

## YAPI 字段支持

解析器会自动遍历 YAPI 返回体，识别包含 `path` 的接口对象，并读取：

- `method`
- `title` / `name`
- `req_params`
- `req_query`
- `req_body_form`
- `req_body_other`

## 代码扫描范围

默认扫描当前仓库下的常见源码文件：

- Java / JavaScript / TypeScript / Vue / Python / Go / Kotlin 等
- JSON / YAML / XML 配置

会自动跳过 `.git`、`node_modules`、`dist`、`build`、`target` 等目录。

## 设计说明

当前实现刻意不引入 Spring、Node、Playwright 等依赖，确保这个仓库可以直接编译和运行。后续如果项目扩展为完整前后端工程，可以把这里的能力拆分为：

- 后端：YAPI 同步、AI 参数生成、接口调用、测试报告
- 前端：接口工作台、参数 diff、执行历史、可视化报告
- 测试执行器：浏览器自动化、登录态管理、CI 集成
