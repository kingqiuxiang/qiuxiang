# APIPilot · AI 接口测试驾驶舱

> 接入 **YAPI** 读取接口参数,**以项目代码为基准**由 AI 一键填充参数,
> 快捷启动项目并访问开发环境,在接口写完之后由 **AI 自动设计并执行测试**,
> 配套**前端页面巡检**。一套精美、流畅的可视化工作台。

原仓库仅有一个 `Main.java` Demo,本项目为该需求从零实现的完整系统。

---

## ✨ 核心能力

| 能力 | 说明 |
| --- | --- |
| 🔌 **接入 YAPI** | 读取接口定义与参数 schema(path / query / header / body)。未配置时内置演示接口,开箱即用。 |
| 🪄 **AI 一键填参** | 以接口契约 **+ 项目代码片段** 为事实基准,调用 AI 生成可直接发起的真实请求参数。未配置 AI 时自动回退到基于字段名/类型的启发式推断。 |
| 📚 **项目代码基准** | 扫描项目代码、按关键字检索,作为 AI 取值依据,并可定位接口相关实现。 |
| 🚀 **AI 快捷启动** | 一键执行配置的启动命令拉起开发环境,实时日志流 + 健康检查。 |
| 🧪 **AI 自动测试** | 写完接口后,AI 自动设计用例(正常 / 参数校验 / 鉴权 / 边界)并执行断言,输出通过率与详情。 |
| 🌐 **前端页面巡检** | 访问开发环境前端页面,检测可达性、标题、SPA 挂载点、资源与错误信号。 |
| 💬 **智能助手** | 内置 AI 对话侧边栏,随时解读接口、协助排错。 |

界面采用深色玻璃拟态 + 渐变 + Framer Motion 流畅动效,交互精美顺滑。

---

## 🧱 技术栈

- **前端**:React 18 + TypeScript + Vite + Tailwind CSS v4 + Framer Motion + lucide-react
- **后端**:Node.js + Express + TypeScript(tsx 运行)
- **AI**:任意 OpenAI 兼容服务(OpenAI / DeepSeek / 通义千问 / Moonshot / 本地 Ollama 等)

```
├── server/        # 后端:YAPI、AI 填参、代码扫描、测试执行、项目启动、页面巡检
│   └── src/
│       ├── services/   # yapi / ai / code / fill / tester / testplan / runner / page
│       ├── config.ts    # 配置读写(data/config.json + 环境变量)
│       └── index.ts     # Express 路由
└── web/           # 前端:驾驶舱 / 接口 / 自动测试 / 项目 / 页面巡检 / 设置
```

---

## 🚀 快速开始

```bash
# 1. 安装依赖(根目录一次性安装前后端)
npm run install:all

# 2. 启动开发模式(后端 :8787 + 前端 :5173,自动代理 /api)
npm run dev
```

浏览器打开 **http://localhost:5173** 即可使用。

> 无需任何配置即可体验:系统内置演示接口,AI 未配置时使用启发式填参与用例生成。

### 生产 / 单进程运行

```bash
npm run build     # 构建前端到 web/dist
npm start         # 后端在 :8787 同时托管前端静态资源
```

---

## ⚙️ 配置

可在页面「设置」中填写,或通过环境变量(见 `server/.env.example`):

| 分组 | 字段 | 用途 |
| --- | --- | --- |
| YAPI | `YAPI_BASE_URL` / `YAPI_TOKEN` / `YAPI_PROJECT_ID` | 拉取真实接口 |
| AI | `AI_BASE_URL` / `AI_API_KEY` / `AI_MODEL` | OpenAI 兼容服务 |
| 项目 | `PROJECT_ROOT` / `PROJECT_START_COMMAND` / `PROJECT_HEALTH_URL` | 代码基准 + 快捷启动 |
| 开发环境 | `DEV_API_BASE_URL` / `DEV_WEB_BASE_URL` | 接口测试 & 页面巡检目标 |

配置保存在 `server/data/config.json`(已加入 `.gitignore`),敏感字段在返回前端时自动脱敏。

---

## 🔄 典型工作流

1. **设置** 中连接 YAPI、AI、项目路径与开发环境;
2. **接口 & 一键填参**:选择接口 → 「AI 一键填参」→ 校对 → 「发送请求」;
3. **AI 自动测试**:对刚写完的接口「生成测试方案」并「一键执行」,查看断言结果;
4. **项目启动 & 代码**:一键拉起服务、看实时日志、健康检查、检索代码基准;
5. **前端页面巡检**:访问开发环境页面验证渲染与可达性。

---

## 🔐 安全说明

- 项目启动通过 shell 执行配置的命令,请仅在受信任的本地开发环境使用。
- API Key / Token 仅保存在本地 `server/data/config.json`,返回前端时脱敏。
