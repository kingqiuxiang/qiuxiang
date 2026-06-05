# AI YAPI 自动联调与测试系统

这是一个可运行的 MVP，用来实现你提出的核心链路：

1. 读取 YAPI 导出的接口参数定义  
2. 结合项目代码上下文进行 AI 一键参数填充  
3. AI 快捷启动项目（开发环境命令）并调用接口  
4. 接口写完后自动执行接口 + 前端页面测试  

## 功能概览

- **精美流畅交互**：提供一体化 Web 控制台（暗色玻璃质感 + 动效）。
- **YAPI 接入**：支持 `JSON 文本 / URL / 本地文件` 三种导入。
- **AI 一键填参**：
  - 未配置大模型时使用本地启发式填参（基于键名 + 项目代码提示）
  - 配置 `AI_API_KEY` 后自动升级为 LLM 填参
- **读取项目代码**：扫描项目源码并提取参数值线索。
- **快捷启动项目**：输入命令即可启动项目进程并查看日志状态。
- **自动测试**：
  - 调用接口并返回状态与响应
  - 使用 Playwright 访问前端页面，支持期望文案检查和截图

---

## 快速开始

```bash
npm install
npm start
```

默认端口：`8787`  
打开：`http://localhost:8787`

### 可选环境变量

复制 `.env.example` 后自行注入环境变量（或直接在运行环境设置）：

- `PORT`
- `AI_API_KEY`
- `AI_BASE_URL`
- `AI_MODEL`

> 不配置 `AI_API_KEY` 也能用（自动走本地 heuristic 填参）。

---

## 主流程（建议）

1. 在页面「导入 YAPI 参数」粘贴 YAPI 导出 JSON 并导入  
2. 选择接口 ID，点击「AI 一键填参」  
3. 在「AI 快速启动并调用项目」输入启动命令（如 `npm run dev`）  
4. 在「自动测试」填写前端页面 URL 与接口调用 URL，点击执行  

---

## 后端 API（核心）

- `POST /api/yapi/load`：导入 YAPI
- `POST /api/params/fill`：AI 填参（结合项目代码）
- `POST /api/run/start`：启动项目命令
- `GET /api/run/:id/log`：获取运行日志
- `POST /api/run/:id/stop`：停止进程
- `POST /api/invoke`：调用接口
- `POST /api/test/execute`：执行接口 + 页面联合测试

---

## 目录结构

```text
public/
  app.js
  index.html
  styles.css
src/
  routes/api.js
  services/
    aiService.js
    codeContextService.js
    projectRunnerService.js
    testOrchestratorService.js
    yapiService.js
  state/store.js
  config.js
  server.js
```

---

## 后续可增强项

- 接入真实 YAPI OpenAPI 拉取与鉴权
- AI 自动生成断言策略（响应结构、字段约束、业务规则）
- 引入任务队列（多环境并发测试）
- 自动生成测试报告（HTML/PDF）与回归基线对比
