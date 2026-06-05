# AI YAPI 一键填参与自动化测试系统（MVP）

这是一个面向接口研发流程的 AI 联调系统，支持：

- 读取 **YAPI 导出 JSON** 接口定义
- 结合 **项目代码上下文** 进行 AI 一键参数填充
- 提供 API 自动测试入口（接口写完后可直接触发）
- 支持通过命令快速启动项目
- 支持前端页面检查（优先 Playwright，异常时自动回退可达性检查）
- 提供流畅、现代化的 Web 控制台页面

## 项目结构

```text
app/
  main.py                # FastAPI 入口
  models.py              # 请求/响应模型
  services/
    yapi_service.py      # YAPI 解析
    code_service.py      # 代码上下文扫描
    ai_service.py        # AI 参数填充
    test_service.py      # API 测试执行
    runtime_service.py   # 项目启动与前端检查
  static/
    index.html           # 单页交互界面
tests/
  test_yapi_service.py
  test_ai_service.py
```

## 快速启动

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

浏览器访问：`http://127.0.0.1:8000`

## AI 配置（OpenAI 兼容）

可选环境变量：

- `AI_BASE_URL`：默认 `https://api.openai.com/v1`
- `AI_API_KEY`：你的 API Key（不设置时会自动使用本地兜底生成）
- `AI_MODEL`：默认 `gpt-4o-mini`

示例：

```bash
export AI_API_KEY=your_key
export AI_MODEL=gpt-4o-mini
```

## 核心 API

- `POST /api/yapi/upload`：上传并解析 YAPI JSON
- `POST /api/code/scan`：扫描项目代码上下文
- `POST /api/ai/fill-params`：AI 一键生成参数
- `POST /api/project/start`：执行项目启动命令
- `POST /api/test/api`：执行接口测试
- `POST /api/test/frontend`：执行前端页面检查

## 前端页面检查说明

- 若安装并配置了 Playwright，会执行真实浏览器 DOM 选择器检查。
- 若 Playwright 未安装或运行失败，会自动回退到 URL 可达性检查。

如需启用完整浏览器测试，可安装：

```bash
pip install playwright
playwright install chromium
```
