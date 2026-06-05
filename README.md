# ApiPilot · AI 接口测试驾驶舱

> 一个拥有精美流畅交互的 AI 接口测试平台。它接入 AI、读取 YAPI 接口定义、以**项目代码为基准**为请求参数做「一键智能填充」，可读取项目代码、驱动开发环境前端页面进行测试，并支持 AI 一键拉起项目、写完接口后自动跑测。

![stack](https://img.shields.io/badge/server-Node%20%2B%20Express-3c873a) ![stack](https://img.shields.io/badge/web-React%20%2B%20Vite-646cff) ![ui](https://img.shields.io/badge/UI-Tailwind%20%2B%20Framer%20Motion-ec4899)

---

## ✨ 它能做什么

| 能力 | 说明 | 对应模块 |
| --- | --- | --- |
| 🎨 精美流畅交互 | 玻璃拟态 / 渐变 / Framer Motion 动效的单页应用 | `web/` |
| 🔌 接入 AI | 兼容 OpenAI 协议（可填任意 base_url / model），无 Key 时自动降级为本地启发式引擎 | `server/src/services/aiService.js` |
| 📥 读取 YAPI 参数 | 拉取 YAPI 项目 / 分类 / 接口，解析 path、query、body、header、JSON-Schema | `server/src/services/yapiService.js` |
| 🧠 一键参数填充（以代码为基准） | 结合 YAPI schema + 项目代码上下文，生成贴近真实业务的请求参数 | `server/src/services/aiService.js` |
| 📚 读取项目代码 | 扫描并索引项目源码，抽取与接口相关的代码片段作为 AI 上下文 | `server/src/services/codeService.js` |
| 🌐 访问前端页面测试 | 访问开发环境前端页面（可选 Playwright 深度驱动，缺省走 HTML 静态分析） | `server/src/services/frontendDriver.js` |
| 🚀 AI 快捷启动项目 | 一键执行项目启动命令，并探活就绪状态 | `server/src/services/projectRunner.js` |
| 🤖 写完接口 AI 自动测试 | 编排：拉取接口 → AI 填参 → 发请求 → AI 分析断言 → 生成报告 | `server/src/services/orchestrator.js` |

## 🏗️ 架构

```
┌─────────────────────────┐        ┌──────────────────────────────────────┐
│  web  (React + Vite)     │  HTTP  │  server (Node + Express)               │
│  - 玻璃拟态/动效 UI       │ ─────► │  /api/config   配置                    │
│  - YAPI 接口浏览          │        │  /api/yapi     拉取 / 解析 YAPI         │
│  - 一键 AI 填参           │        │  /api/code     扫描项目代码             │
│  - 发送 & 自动测试        │  SSE   │  /api/ai       一键填参 / 结果分析      │
│  - 实时日志 & 报告        │ ◄───── │  /api/test     发请求 / 前端页面测试    │
└─────────────────────────┘        │  /api/project  启动 / 探活             │
                                    └──────────────────────────────────────┘
```

## 🚀 快速开始

```bash
# 1. 安装依赖（根目录一键装好 server + web）
npm install
npm run setup

# 2. 启动（同时拉起后端 4178 与前端 5178）
npm run dev

# 打开 http://localhost:5178
```

> 首次进入会加载内置的 YAPI 样例数据与示例项目，**无需任何外部依赖即可体验全流程**。
> 在「设置」中填入真实的 YAPI 地址/Token、AI base_url/key/model、项目路径与启动命令即可对接真实环境。

### 环境变量（可选，见 `server/.env.example`）

```
PORT=4178
AI_BASE_URL=https://api.openai.com/v1
AI_API_KEY=sk-...
AI_MODEL=gpt-4o-mini
```

未配置 `AI_API_KEY` 时，AI 填参 / 分析会自动使用**本地启发式引擎**（基于 YAPI schema + 代码常量），保证离线可用、可演示。

## 🧪 内置可跑的端到端 Demo

样例项目 `server/sample-project/` 自带一个最小的接口服务（`/api/user/login`、`/api/order/create` 等）。在 UI 里点击 **「AI 自动测试」**，即可看到：拉取接口 → 读代码 → AI 填参 → 发请求 → AI 断言 → 报告 的完整链路。

## 📁 目录结构

```
.
├── server/                 # 后端
│   ├── src/
│   │   ├── index.js        # 入口
│   │   ├── config.js       # 运行时配置（env + 持久化）
│   │   ├── routes/         # 路由
│   │   └── services/       # YAPI / 代码 / AI / 测试 / 启动 / 编排
│   ├── sample-data/        # YAPI 样例导出
│   └── sample-project/     # 可一键启动的示例接口项目
├── web/                    # 前端（React + Vite + Tailwind + Framer Motion）
└── package.json            # 根脚本（并发启动）
```

## License

MIT
