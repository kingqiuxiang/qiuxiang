# XMind MCP · 让 Cursor 直接画思维导图

一个 **MCP（Model Context Protocol）服务**：在 Cursor 里用自然语言描述需求，AI 自动生成 `.xmind` 思维导图文件，并**用你本地的 XMind 直接打开**。专为 **Windows** 调通（同时兼容 macOS / Linux）。

> 一句话用法：在 Cursor 对话框里说「**用 XMind 帮我画一张「电商系统」的架构脑图**」，文件会自动生成并在本地 XMind 弹出。

---

## ✨ 能力

- 🧠 **AI → 思维导图**：把需求转成层级化的 `.xmind` 文件（现代 XMind ZIP/JSON 格式，XMind 8 / Zen / 2020+ / 2022+ 均可打开）。
- 🚀 **自动拉起本地 XMind**：生成后通过 Windows 文件关联自动打开；找不到关联时回退到常见 `XMind.exe` 安装路径。
- ✍️ **两种输入**：
  - `outline`：Markdown 标题 / 缩进列表大纲（**推荐**，AI 最容易生成）。
  - `tree`：结构化 JSON 树（精确控制层级与备注）。
- 📝 **支持备注**：每个节点可带 note（XMind 里的「备注」）。
- 🗂️ **多画布**：一个文件可包含多张 sheet。

## 📦 环境要求

- 本地已安装 **XMind**（任意现代版本）。
- **Node.js ≥ 18**（建议 18/20/22）。Windows 下从 [nodejs.org](https://nodejs.org) 安装即可。

---

## 🚀 安装

```bash
# 进入该目录
cd xmind-mcp

# 安装依赖
npm install

# 自检（不需要安装 XMind 也能跑）
npm test
```

记下本目录里 `src/index.js` 的**绝对路径**，下一步配置要用。例如：

```
C:\Users\你的用户名\path\to\xmind-mcp\src\index.js
```

---

## 🔧 在 Cursor 里配置（Windows）

Cursor 通过 `mcp.json` 加载 MCP 服务。打开（或新建）配置文件：

- **项目级**：在项目根目录创建 `.cursor/mcp.json`
- **全局级**：`C:\Users\<你的用户名>\.cursor\mcp.json`

填入以下内容（把路径换成你机器上的**绝对路径**，Windows 路径里的反斜杠要写成 `\\`）：

```json
{
  "mcpServers": {
    "xmind": {
      "command": "node",
      "args": ["C:\\Users\\你的用户名\\path\\to\\xmind-mcp\\src\\index.js"],
      "env": {
        "XMIND_OUTPUT_DIR": "C:\\Users\\你的用户名\\Desktop\\XMind"
      }
    }
  }
}
```

> `XMIND_OUTPUT_DIR` 可选，用来指定 `.xmind` 文件默认保存目录。不填则默认保存到 **桌面\XMind**（找不到桌面时用系统临时目录）。

保存后，到 Cursor **Settings → MCP**（或 Tools/Integrations）里确认 `xmind` 服务为已连接/绿色，必要时点一下刷新。

### 如果自动打开失败怎么办？

绝大多数情况下 Windows 会用文件关联自动打开 XMind。若你的环境关联缺失，可在调用时（或让 AI）传入 `xmindAppPath` 指向 `XMind.exe`，例如：

```
C:\Program Files\XMind\XMind.exe
```

服务也会自动尝试这些常见安装路径：`Program Files\XMind\XMind.exe`、`Program Files (x86)\XMind\XMind.exe`、`%LOCALAPPDATA%\Programs\XMind\XMind.exe` 等。

---

## 🗣️ 怎么用（在 Cursor 对话里）

配置好后，直接对 Cursor 说人话即可，例如：

- 「用 XMind 给我画一张『用户注册登录流程』的思维导图。」
- 「按这份需求生成 XMind 脑图：……（贴上需求）」
- 「把刚才的方案整理成 XMind，分『前端 / 后端 / 数据库』三个分支。」

Cursor 会调用本服务的工具，生成文件并自动用本地 XMind 打开。

---

## 🧰 工具（Tools）说明

### 1. `create_mindmap` — 生成并打开思维导图

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `outline` | string | Markdown/缩进大纲。`#` = 中心主题，`##` / 缩进列表 = 层级。与 `tree` 二选一。 |
| `tree` | object/array | 结构化树。节点字段：`title`、可选 `note`、可选 `children[]`。与 `outline` 二选一。 |
| `title` | string | 中心主题标题，可覆盖大纲/树的根标题，并作为默认文件名。 |
| `filename` | string | 输出文件名（可不带 `.xmind`）。默认取 `title`。 |
| `outputDir` | string | 输出目录绝对路径。默认：`XMIND_OUTPUT_DIR` > 桌面\XMind > 临时目录。 |
| `open` | boolean | 生成后是否自动打开，默认 `true`。 |
| `xmindAppPath` | string | 可选：`XMind.exe` 绝对路径（关联缺失时兜底）。 |

**`outline` 示例：**

```markdown
# 电商系统
## 用户中心
- 注册
  - 手机号
  - 邮箱
- 登录
## 商品中心
- 商品管理
- 库存
## 订单中心
- 下单
- 支付
- 售后
```

**`tree` 示例：**

```json
{
  "title": "电商系统",
  "children": [
    { "title": "用户中心", "children": [{ "title": "注册", "note": "支持手机号/邮箱" }] },
    { "title": "订单中心", "children": [{ "title": "支付" }] }
  ]
}
```

### 2. `open_xmind` — 打开已存在的 `.xmind`

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `filePath` | string | `.xmind` 文件绝对路径。 |
| `xmindAppPath` | string | 可选：`XMind.exe` 绝对路径。 |

---

## 🖥️ 跨平台说明

| 系统 | 打开方式 |
| --- | --- |
| **Windows** | `cmd /c start` 走文件关联；失败回退已知 `XMind.exe` 路径。 |
| macOS | `open -a XMind`，回退系统默认程序。 |
| Linux | `xdg-open`。 |

---

## ❓ 常见问题

- **生成的文件 XMind 打不开 / 报损坏？** 本服务生成的是现代 XMind 的 ZIP+JSON 格式，请使用 XMind 8 及以后版本打开。
- **Cursor 里看不到工具？** 检查 `mcp.json` 路径是否为绝对路径、Node 是否在 PATH 中，并在 Cursor 的 MCP 设置里刷新/重连。
- **想改默认保存位置？** 设置环境变量 `XMIND_OUTPUT_DIR`，或调用时传 `outputDir`。

## 🛠️ 开发自检

```bash
npm run smoke   # 校验大纲解析 + .xmind 生成
npm test        # smoke + MCP stdio 握手/工具调用
```
