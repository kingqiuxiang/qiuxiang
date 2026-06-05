# xmind-mcp · 让 Cursor 直接生成思维导图并用本地 XMind 打开

一个本地 **MCP（Model Context Protocol）服务**：在 Cursor 中用自然语言描述需求，AI 即可生成 `.xmind` 思维导图文件，并**自动调用你本地安装的 XMind 打开**。Windows 优先支持，同时兼容 macOS / Linux。

```
你在 Cursor 里说：「帮我画一张『电商系统架构』的思维导图」
        ↓
Cursor 调用本 MCP 的 create_mindmap 工具
        ↓
生成 电商系统架构.xmind（默认放在桌面）
        ↓
自动用本地 XMind 打开 ✅
```

---

## 1. 环境要求

- **Node.js ≥ 18**（建议 18/20/22）。在命令行执行 `node -v` 确认。
- 本地已安装 **XMind** 桌面端。
- Cursor（用于配置 MCP）。

## 2. 安装

```bash
# 进入本目录
cd xmind-mcp

# 安装依赖
npm install
```

> 提示：记下本目录的**绝对路径**，下一步配置要用。例如：
> `C:\Users\你的用户名\code\项目\xmind-mcp`

## 3. 在 Cursor 中配置 MCP（Windows）

Cursor 的 MCP 配置文件位置：

- 项目级：项目根目录下 `.cursor/mcp.json`
- 全局级：`%USERPROFILE%\.cursor\mcp.json`（即 `C:\Users\你的用户名\.cursor\mcp.json`）

新建/编辑该文件，加入如下内容（把路径换成你机器上 `xmind-mcp/src/index.js` 的**绝对路径**，Windows 路径中的反斜杠要写成 `\\`）：

```json
{
  "mcpServers": {
    "xmind": {
      "command": "node",
      "args": ["C:\\Users\\你的用户名\\code\\项目\\xmind-mcp\\src\\index.js"]
    }
  }
}
```

如果 XMind 未安装在默认位置、或自动打开失败，可显式指定 XMind 可执行文件路径（可选）：

```json
{
  "mcpServers": {
    "xmind": {
      "command": "node",
      "args": ["C:\\路径\\xmind-mcp\\src\\index.js"],
      "env": {
        "XMIND_PATH": "C:\\Users\\你的用户名\\AppData\\Local\\Programs\\XMind\\XMind.exe"
      }
    }
  }
}
```

保存后，到 Cursor 设置 → **MCP**（或 Tools）里确认 `xmind` 服务为已连接/绿灯状态。若没出现，重启 Cursor。

### macOS / Linux 配置

把 `args` 换成你的绝对路径即可，例如 macOS：

```json
{
  "mcpServers": {
    "xmind": {
      "command": "node",
      "args": ["/Users/you/code/xmind-mcp/src/index.js"]
    }
  }
}
```

## 4. 使用

直接在 Cursor 对话里下达需求，AI 会自动调用工具。例如：

- 「用 XMind 帮我画一张『2024 营销活动策划』的思维导图」
- 「把这段需求整理成思维导图并打开：……」
- 「生成项目 OKR 脑图，分四个目标，每个目标 3 条关键结果」

生成的文件**默认保存到 Windows 桌面**（其他系统为用户主目录下 `xmind-mcp/`），并自动用 XMind 打开。

## 5. 工具说明（供 AI / 进阶用户）

### `create_mindmap` — 生成并打开思维导图

参数（`outline` 与 `topics` 二选一）：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `title` | string | 中心主题。用 `outline` 且首行即标题时可省略 |
| `outline` | string | **Markdown / 缩进大纲**文本（推荐） |
| `topics` | object[] | **结构化主题树**（需要备注/图标/超链接时用） |
| `filename` | string | 输出文件名（可省略 `.xmind`） |
| `directory` | string | 输出目录绝对路径（默认桌面） |
| `open` | boolean | 是否生成后立即用 XMind 打开，默认 `true` |

**outline 大纲格式**（两种写法可混用）：

```
# 中心主题
## 分支一
- 要点 A
  - 子要点 A1
- 要点 B
## 分支二
- 要点 C
```

深度规则：

- `#`…`######` 标题：深度 = `#` 个数 − 1（`#` 为根）。
- 列表 / 普通行：深度 = 最近标题深度 + 1 + （前导空格数 ÷ 2，向下取整）。Tab 记作 2 个空格。
- 也支持纯缩进写法（无 `#`），首行为中心主题，每级缩进 2 空格。

**topics 结构化格式**（支持备注、图标、超链接）：

```json
{
  "title": "中心主题",
  "topics": [
    {
      "title": "分支一",
      "note": "这里是备注说明",
      "marker": "priority-1",
      "children": [
        { "title": "子要点", "href": "https://example.com" }
      ]
    },
    { "title": "分支二", "marker": ["flag-red", "star-blue"] }
  ]
}
```

常用图标 `markerId`：`priority-1`…`priority-9`、`task-done` / `task-quarter` / `task-half` / `task-3quar`、`flag-red` / `flag-green`、`star-blue` / `star-yellow`、`symbol-question` / `symbol-exclam` 等。

### `open_xmind` — 打开已有文件

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `filePath` | string | `.xmind` 文件的绝对路径 |

## 6. 自检 / 排错

本地自测（不依赖 Cursor，验证生成逻辑与 MCP 握手）：

```bash
npm run smoke
```

常见问题：

- **Cursor 里看不到 xmind 工具**：确认 `mcp.json` 路径正确、为绝对路径、Windows 用 `\\`；重启 Cursor。
- **生成成功但没自动打开**：设置 `XMIND_PATH` 指向 `XMind.exe`；或手动双击桌面上的 `.xmind` 文件确认关联程序为 XMind。
- **想换默认保存目录**：调用时传 `directory`，或设置环境变量 `XMIND_OUTPUT_DIR`（非 Windows 生效）。
- **`node` 不是内部命令**：未安装 Node 或未加入 PATH，安装 Node.js 后重开终端/Cursor。

## 7. 工作原理

`.xmind` 文件本质是一个 zip 包，核心是 `content.json`（XMind 2020/Zen 及之后版本的画布与主题树数据）。本服务用 `jszip` 按官方结构打包 `content.json` / `metadata.json` / `manifest.json`，生成可被 XMind 直接打开的文件，再通过系统命令（Windows 上优先定位 `XMind.exe`，否则 `start` 默认关联程序）拉起 XMind。

## 目录结构

```
xmind-mcp/
├── package.json
├── README.md
└── src/
    ├── index.js     # MCP stdio 服务入口（注册 create_mindmap / open_xmind）
    ├── xmind.js     # .xmind（content.json）生成 + zip 打包
    ├── outline.js   # Markdown / 缩进大纲 与 结构化树 解析
    └── open.js      # 跨平台（Windows 优先）调用本地 XMind 打开
```
