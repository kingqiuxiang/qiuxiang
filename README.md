# 灵测 LingCe · AI 接口智测平台

> 读取 **YAPI** 接口定义 → 结合**项目源码**由 **AI 一键填充测试参数** → **一键启动**项目 → **自动发起接口请求** → **AI 评审测试结果**，并可访问开发环境前端页面进行检测。

一个为研发/测试打造的、拥有精美流畅交互的 AI 驱动接口测试平台。前后端分离（React + Express），开箱即用，未配置 YAPI / AI 时自动进入**演示模式**，全部功能可直接体验。

---

## ✨ 核心能力

| 能力 | 说明 |
| --- | --- |
| 📚 **读取 YAPI** | 通过 YAPI Open API 拉取项目下所有接口及其请求/响应定义 |
| 🪄 **AI 一键参数填充** | 以「接口定义 + 项目源码上下文」为基准，调用 AI 生成真实、合理、可直接发起请求的测试参数 |
| 🧠 **代码感知** | 自动扫描项目源码、定位与接口路径相关的代码片段，作为 AI 上下文（复用真实枚举值/默认值） |
| 🚀 **快捷启动** | 一键运行项目启动命令，实时流式查看启动日志（SSE） |
| 🧪 **自动测试** | 写完接口后，一键让 AI 批量「填参 → 发请求 → 评审」，输出通过率与问题列表 |
| 🖥️ **前端页面测试** | 访问开发环境前端页面，检测可达性与渲染情况（可选启用 Playwright 真实浏览器） |
| 🎨 **精美交互** | 玻璃拟态 + 渐变 + Framer Motion 流畅动效的现代深色界面 |

## 🧭 工作流

```
选择/创建项目  →  接口库(YAPI)  →  AI 测试台(一键填参/调试)  →  快捷启动项目  →  AI 全量自动测试  →  测试历史/AI 评审
```

## 🏗️ 技术栈

- **前端**：React 18 · TypeScript · Vite · TailwindCSS · Framer Motion · Zustand · lucide-react
- **后端**：Node.js · Express · TypeScript（tsx 运行）· SSE 实时日志
- **AI**：任意 OpenAI 兼容接口（OpenAI / DeepSeek / 通义千问 / Moonshot 等）
- **存储**：本地 JSON 文件（零依赖，免数据库）
- **浏览器自动化（可选）**：Playwright

## 📂 目录结构

```
.
├── server/                 # 后端
│   └── src/
│       ├── index.ts        # Express 入口 / 路由 / SSE
│       ├── store.ts        # JSON 持久化
│       ├── types.ts
│       └── services/
│           ├── yapi.ts     # YAPI 接口拉取（含演示降级）
│           ├── ai.ts       # AI 填参 & 结果评审（含启发式降级）
│           ├── code.ts     # 源码扫描 & 接口代码定位
│           ├── tester.ts   # 接口请求执行
│           ├── runner.ts   # 项目进程管理 & 日志
│           ├── browser.ts  # 前端页面检测
│           └── demo.ts     # 内置演示接口
├── web/                    # 前端（Vite + React）
│   └── src/{pages,components,lib}
├── idea-plugin/            # IntelliJ IDEA 插件：AI/临时/可疑文件识别与清理
└── package.json            # 一键安装 / 启动两端
```

## 🚀 快速开始

环境要求：Node.js ≥ 18。

```bash
# 1. 安装依赖（会自动安装 server 与 web 两端）
npm install

# 2. 启动开发模式（后端 :8787 + 前端 :5173，前端已配置 /api 代理）
npm run dev
# 打开 http://localhost:5173

# —— 或者 生产模式 ——
npm run build          # 构建前端到 web/dist
npm start              # 后端在 :8787 同时托管前端，打开 http://localhost:8787
```

### 配置（两种方式任选）

1. **可视化配置（推荐）**：进入页面右上角「项目管理 → 新建项目」，填写 YAPI、AI、源码路径、开发环境地址、启动命令。
2. **环境变量默认值**：复制 `.env.example` 为 `.env`，填写默认 AI / YAPI，新建项目时会自动带入。

```bash
PORT=8787
AI_BASE_URL=https://api.openai.com/v1
AI_API_KEY=sk-xxx
AI_MODEL=gpt-4o-mini
YAPI_BASE_URL=http://yapi.your-company.com
YAPI_TOKEN=your_project_token
```

> 未配置 YAPI / AI 时，平台自动进入**演示模式**：使用内置接口与启发式参数生成，完整体验全流程。

### 启用真实浏览器页面检测（可选）

```bash
cd server && npm i playwright && npx playwright install chromium
```
安装后「前端页面测试」会自动切换为 Playwright 真实渲染检测（含 JS 报错捕获、根节点挂载校验）。

## 🔌 项目配置项说明

| 字段 | 含义 | 示例 |
| --- | --- | --- |
| YAPI 地址 / Token | YAPI Open API 连接信息 | `http://yapi.xx.com` / `xxxxxx` |
| AI Base URL / Key / Model | OpenAI 兼容模型配置 | `https://api.openai.com/v1` / `sk-...` / `gpt-4o-mini` |
| 项目源码路径 | 服务器上项目代码**绝对路径**，供 AI 读取上下文 | `/home/me/app` |
| devBaseUrl | 开发环境后端基础地址 | `http://localhost:8080` |
| devWebUrl | 开发环境前端地址 | `http://localhost:3000` |
| 启动命令 | 一键启动所执行命令 | `npm run dev` / `mvn spring-boot:run` |

## 📡 主要 API

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/api/projects` | 项目列表 |
| `POST/PUT/DELETE` | `/api/projects/:id` | 项目增改删 |
| `GET` | `/api/projects/:id/interfaces` | 拉取 YAPI 接口列表 |
| `POST` | `/api/projects/:id/interfaces/:iid/fill` | AI 一键参数填充 |
| `POST` | `/api/projects/:id/interfaces/:iid/test` | 执行单接口测试 + AI 评审 |
| `POST` | `/api/projects/:id/autotest` | AI 批量自动测试 |
| `GET` | `/api/projects/:id/runner/stream` | SSE 实时启动日志 |
| `POST` | `/api/projects/:id/page-test` | 前端页面检测 |

## 🔒 说明

- 所有密钥仅保存在本地 `server/data/db.json`，不会上传第三方。
- 平台会向你配置的「开发环境地址」实际发起 HTTP 请求，请确保仅指向你有权访问的内网/本地服务。
