# XMind Local MCP（Windows + Cursor）

这个 MCP 服务用于让 Cursor 直接调用本地 XMind：

1. 根据需求文本生成 `.xmind` 文件  
2. 自动拉起 Windows 本地 XMind 打开该文件

---

## 1) 环境准备（Windows 本机）

- 已安装 **Node.js 18+**
- 已安装 **XMind**
- 在项目目录安装依赖：

```bash
cd mcp/xmind-local
npm install
```

---

## 2) 配置到 Cursor

在你的 Cursor MCP 配置文件里添加一个 server（可用项目级 `.cursor/mcp.json`，或用户级配置）。

> 注意把下面路径替换成你自己 Windows 本机的真实路径。

```json
{
  "mcpServers": {
    "xmind-local": {
      "command": "node",
      "args": ["D:\\\\workspace\\\\mcp\\\\xmind-local\\\\src\\\\index.js"],
      "env": {
        "XMIND_EXECUTABLE": "C:\\\\Program Files\\\\Xmind\\\\Xmind.exe",
        "XMIND_OUTPUT_DIR": "D:\\\\xmind-output"
      }
    }
  }
}
```

### 环境变量说明

- `XMIND_EXECUTABLE`（可选但推荐）  
  XMind 可执行文件路径。设置后会优先用它打开脑图。
- `XMIND_OUTPUT_DIR`（可选）  
  默认输出目录。不设置时默认写到 `~/Documents/xmind-mcp`。

---

## 3) MCP 工具能力

### `draw_xmind`

根据 `requirement`（自然语言）或 `outline`（结构化树）生成脑图。

核心参数：

- `title`：中心主题（必填）
- `requirement`：需求文本（可选）
- `outline`：结构化节点（可选，优先级高于 requirement）
- `outputPath`：输出路径（可选）
- `openAfterCreate`：生成后是否自动打开（默认 true）
- `xmindExePath`：本次调用指定 XMind.exe（可选）

### `open_xmind_file`

打开已有 `.xmind` 文件。

---

## 4) 在 Cursor 里如何提需求

你可以直接对 Cursor 说：

- “调用 `draw_xmind`，主题是《需求评审》，把以下需求转成脑图并自动打开……”
- “调用 `draw_xmind`，输出到 `D:\\product\\prd.xmind`，不要自动打开”
- “调用 `open_xmind_file` 打开 `D:\\product\\prd.xmind`”

---

## 5) 常见问题

### 1. 没有自动打开 XMind

- 优先检查 `XMIND_EXECUTABLE` 路径是否正确。
- 若未设置该变量，会回退到系统文件关联打开。

### 2. 脑图结构不理想

- 直接传 `outline`（结构化树）可获得更稳定结果。
- `requirement` 文本建议使用分点格式（例如 `- 一级`、`  - 二级`），解析效果最好。
