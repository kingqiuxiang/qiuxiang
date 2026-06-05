# AI 接口联调系统（YAPI + 项目代码 + 一键 AI 测试）

这是一个可直接运行的 MVP 系统，目标是满足你提到的流程：

1. **读取 YAPI 接口参数**
2. **基于项目代码做 AI 一键参数填充**
3. **AI 快捷启动项目并调用接口**
4. **写完接口后，自动进行 API + 前端页面测试**
5. **提供流畅交互的 Web 控制台**

## 功能总览

- **YAPI 解析**
  - 支持粘贴 YAPI JSON（也支持 URL 拉取）
  - 解析接口：method/path/query/path/header/body schema
- **AI 参数填充**
  - 优先走大模型（通过 `OPENAI_API_KEY`）
  - 未配置密钥时自动降级启发式填充（可立即使用）
- **项目启动编排**
  - 通过命令一键启动项目并跟踪日志
  - 支持就绪关键字检测（例如 `ready`, `started`, `listening`）
- **自动测试**
  - API 自动请求（method/path/query/body）
  - UI 冒烟测试（优先 Playwright，未安装时降级可访问性检测）
- **一键工作流**
  - 在 “接口写完” 后执行：
    `启动项目 -> AI 填参 -> API 测试 -> 前端页面测试`

## 目录结构

```text
app/
  main.py                      # FastAPI 入口
  schemas.py                   # 请求/响应模型
  static/
    index.html                 # 交互界面
    styles.css                 # 流畅视觉样式
    app.js                     # 前端流程编排
  services/
    yapi_parser.py             # YAPI 结构解析
    code_context.py            # 项目代码上下文提取
    ai_client.py               # LLM 调用封装
    parameter_filler.py        # AI/规则参数填充
    project_runner.py          # 项目启动/停止/状态管理
    tester.py                  # API/UI 测试执行
tests/
  test_yapi_parser.py
  test_parameter_filler.py
```

## 快速启动

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

浏览器打开：`http://127.0.0.1:8000`

## AI 配置（可选）

如果需要真实大模型填参，设置：

```bash
export OPENAI_API_KEY=your_key
export OPENAI_BASE_URL=https://api.openai.com/v1
export OPENAI_MODEL=gpt-4o-mini
```

未配置时，系统会自动使用启发式规则生成可用参数。

## 核心接口

- `POST /api/yapi/parse`：解析 YAPI
- `POST /api/ai/fill-params`：AI 一键参数填充
- `POST /api/project/start`：快捷启动项目
- `POST /api/project/stop`：停止项目
- `GET /api/project/status/{project_name}`：查看进程状态
- `POST /api/test/api`：执行接口测试
- `POST /api/test/ui`：执行页面测试
- `POST /api/workflow/after-interface`：一键编排（写完接口后测试）

## 常见下一步扩展

- 接入真实 YAPI Token 鉴权与项目列表检索
- 引入多模型路由（OpenAI / Azure / 私有模型）
- 加入测试脚本录制与回放（Playwright Trace）
- 增加接口回归基线与结果对比面板
- 与 CI 平台集成，自动在 PR 阶段执行
