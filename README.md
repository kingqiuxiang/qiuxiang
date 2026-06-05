# ⚡ AI YAPI 测试平台

> 写完接口，剩下交给 AI 测试。

一个 **AI 驱动的接口测试平台**：自动读取 **YAPI** 接口定义，**以项目真实代码为基准** 用 AI 一键生成请求参数，并对开发环境的 **后端接口** 与 **前端页面** 执行自动化测试。配套精美、流畅的可视化交互界面。

![stack](https://img.shields.io/badge/server-Express%20%2B%20TypeScript-blue) ![stack](https://img.shields.io/badge/web-React%20%2B%20Vite%20%2B%20Tailwind-magenta)

---

## 核心能力

| 能力 | 说明 | 对应实现 |
| --- | --- | --- |
| 📥 读取 YAPI 参数 | 通过 YAPI Open API 拉取项目接口、参数、请求/响应 Schema；未配置时使用内置演示数据 | `server/src/services/yapiClient.ts` |
| 🧠 AI 一键参数填充 | 结合接口定义 + 项目代码证据，调用大模型生成贴近业务的参数；未配置 Key 时回退内置启发式 | `server/src/services/paramFiller.ts`、`aiClient.ts` |
| 📂 以项目代码为基准 | 扫描 `PROJECT_ROOT`，按接口路径/字段名定位相关代码片段，作为 AI 填参的证据 | `server/src/services/codeIndexer.ts` |
| 🧪 接口自动化测试 | 用填充参数构造并发起真实请求，对状态码、JSON 结构、YAPI 响应字段做断言 | `server/src/services/testRunner.ts` |
| 🌐 前端页面测试 | 访问开发环境前端页面，捕获控制台报错并截图（Playwright 可选，缺省回退 HTTP 冒烟） | `server/src/services/frontendTester.ts` |
| 🚀 项目快捷启动 | 一键执行项目启动命令并轮询开发环境就绪状态，日志实时回传 | `server/src/services/projectRunner.ts` |
| 🤖 写完即测（Autopilot） | 串联「读取定义 → AI 填参 → 发起请求 → 校验响应 → 页面冒烟」全流程 | `server/src/services/autopilot.ts` |
| 🛰 实时控制台 | 通过 WebSocket 推送运行日志，前端实时渲染 | `server/src/utils/events.ts` |

> 设计上 **开箱即用**：即使不配置 YAPI / AI / 项目，也能用内置演示数据完整体验全流程。

---

## 目录结构

```
.
├── server/                 # 后端（Express + TypeScript）
│   └── src/
│       ├── config/         # 环境配置
│       ├── services/       # YAPI / AI / 代码索引 / 测试 / 自动驾驶
│       ├── routes/         # REST API
│       ├── utils/          # 日志 / Schema 示例生成 / WebSocket
│       └── index.ts        # 入口（同时托管前端构建产物）
├── web/                    # 前端（React + Vite + Tailwind + Framer Motion）
│   └── src/
│       ├── pages/          # 概览 / 接口工作台 / 自动测试 / 配置
│       ├── components/     # UI 组件 / 实时控制台
│       ├── hooks/          # WebSocket 事件流
│       └── lib/api.ts      # 类型与 API 封装
└── .env.example            # 配置模板
```

---

## 快速开始

```bash
# 1. 安装依赖（npm workspaces，一次装好前后端）
npm install

# 2. 复制配置（可全部留空，使用演示数据体验）
cp .env.example .env

# 3a. 开发模式（前端 5180 + 后端 8787，热更新）
npm run dev

# 3b. 生产模式（构建前端 + 由后端统一托管）
npm run build
npm start
# 打开 http://localhost:8787
```

> 需要 Node.js ≥ 20。

---

## 配置说明（`.env`）

| 变量 | 作用 | 留空行为 |
| --- | --- | --- |
| `YAPI_BASE_URL` / `YAPI_TOKEN` | YAPI 站点与项目 token | 使用内置演示接口 |
| `AI_BASE_URL` / `AI_API_KEY` / `AI_MODEL` | OpenAI 兼容大模型（含国产网关） | 使用内置启发式生成参数 |
| `PROJECT_ROOT` | 被测项目源码目录（AI 填参基准） | 不读取代码证据 |
| `DEV_API_BASE_URL` | 开发环境后端地址（接口测试目标） | `http://localhost:3000` |
| `DEV_WEB_BASE_URL` | 开发环境前端地址（页面测试目标） | `http://localhost:5173` |
| `PROJECT_START_COMMAND` | 一键启动项目的命令 | 无法快捷启动 |

> 启用真实浏览器页面测试：在 `server` 下执行 `npm i playwright && npx playwright install chromium`，平台会自动改用 Playwright 渲染并截图。

---

## 主要 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/status` | 平台与配置状态 |
| GET | `/api/yapi/interfaces` | 接口列表 |
| GET | `/api/yapi/interfaces/:id` | 接口详情 |
| GET | `/api/code/evidence/:id` | 接口相关代码证据 |
| POST | `/api/ai/fill/:id` | AI 一键填充参数 |
| POST | `/api/test/:id` | 执行接口测试 |
| POST | `/api/test-page` | 前端页面冒烟测试 |
| POST | `/api/autopilot/:id` | 单接口写完即测 |
| POST | `/api/autopilot` | 批量自动测试全部接口 |
| POST | `/api/project/start` · `/stop` | 快捷启动 / 停止项目 |
| WS | `/ws` | 运行日志实时事件流 |

---

## 开发

```bash
npm test     # 运行后端单元测试（vitest）
npm run lint # 类型检查（前后端）
npm run build
```

---

## 工作流（写完即测）

```
接口写好 ──▶ 平台读取 YAPI 定义
           └─▶ 扫描项目代码，定位相关片段（基准）
                 └─▶ AI 依据定义 + 代码生成参数
                       └─▶ 发起接口请求并断言响应
                             └─▶ 可选：打开前端页面冒烟
                                   └─▶ 实时控制台输出 + 通过/失败报告
```
