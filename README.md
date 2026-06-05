# AI 接口联测助手（YAPI + AI + 项目代码 + 前端联测）

这是一个可直接运行的系统原型，目标是满足以下流程：

1. 读取 YAPI 参数定义（文件或 URL）
2. 基于项目代码上下文进行 AI 一键参数填充
3. AI 快捷启动项目服务
4. 写完接口后自动执行接口测试，并可联测开发环境前端页面

## 主要能力

- **精简流畅的 Web 交互台**
  - 单页面串联完整流程：导入 YAPI -> 选接口 -> 一键填参 -> 启动项目 -> 联测
- **YAPI 参数解析**
  - 支持常见导出结构：`list / data.list / items / apis`
  - 解析 query/path/body 参数
- **AI 一键参数填充**
  - 可接 OpenAI 兼容接口（`OPENAI_API_KEY` / `OPENAI_BASE_URL` / `OPENAI_MODEL`）
  - 未配置模型时自动回退为本地规则填充，保证流程可跑通
- **项目代码基准填参**
  - 读取项目源码片段，结合接口参数名构建上下文
- **联测执行**
  - 发起接口请求并返回响应
  - 可额外请求前端开发地址并抓取页面状态与标题
- **快捷启动**
  - 支持命令一键拉起项目进程，并提供运行状态查询

## 技术栈

- FastAPI（后端服务）
- 原生 HTML/CSS/JS（交互前端）
- httpx（HTTP 调用）

## 快速启动

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app:app --reload --port 8000
```

打开浏览器访问：

```text
http://127.0.0.1:8000
```

## 环境变量（可选）

- `OPENAI_API_KEY`：接入真实 AI
- `OPENAI_BASE_URL`：默认 `https://api.openai.com/v1`
- `OPENAI_MODEL`：默认 `gpt-4o-mini`

## API 端点

- `POST /api/yapi/import`：导入 YAPI
- `POST /api/params/autofill`：AI 一键填参
- `POST /api/project/quick-start`：快捷启动
- `GET /api/project/status`：进程状态
- `POST /api/tests/run`：接口 + 前端联测

## 测试

```bash
python -m unittest discover -s tests -p "test_*.py"
```
