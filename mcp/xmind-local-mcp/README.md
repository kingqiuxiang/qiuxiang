# xmind-local-mcp

一个可在 Cursor 中使用的 MCP Server：  
把需求文本/结构化大纲转换为 `.xmind`，并在本机自动拉起 XMind 打开文件（重点支持 Windows）。

## 1) 安装

```bash
cd mcp/xmind-local-mcp
npm install
```

## 2) 在 Cursor 配置 MCP（Windows）

在你的 Windows 本机上编辑 Cursor 的 MCP 配置文件：

- 通常位置：`%USERPROFILE%\.cursor\mcp.json`

示例配置（请把路径改成你自己的本地绝对路径）：

```json
{
  "mcpServers": {
    "xmind-local": {
      "command": "node",
      "args": [
        "D:\\\\your-repo-path\\\\mcp\\\\xmind-local-mcp\\\\src\\\\index.js"
      ],
      "env": {
        "XMIND_EXECUTABLE": "C:\\\\Program Files\\\\Xmind\\\\Xmind.exe"
      }
    }
  }
}
```

说明：

- `XMIND_EXECUTABLE` 可选，不填则走 Windows 文件关联打开 `.xmind`。
- 如果你机器里 XMind 安装路径不同，请修改为真实路径。

## 3) 可用工具

### `create_xmind_from_text`

根据需求文本快速生成脑图。

输入参数：

- `title`：脑图标题（默认“需求脑图”）
- `requirementText`：需求原文
- `outputPath`：输出文件路径（可选）
- `openAfterCreate`：是否自动打开（默认 `true`）
- `xmindExecutable`：可执行文件路径（可选，会覆盖环境变量）

### `create_xmind_from_outline`

根据结构化大纲生成层级更准确的脑图。

输入参数：

- `title`：脑图标题
- `outline`：树形数组（`title` / `note` / `children`）
- `outputPath`：输出文件路径（可选）
- `openAfterCreate`：是否自动打开（默认 `true`）
- `xmindExecutable`：可执行文件路径（可选，会覆盖环境变量）

## 4) 在 Cursor 中怎么提问

你可以直接对 Cursor 说：

- “调用 `create_xmind_from_text`，标题是『用户登录需求』，根据下面这段需求生成脑图并打开 XMind……”
- “先把这段 PRD 整理成结构化大纲，再调用 `create_xmind_from_outline` 生成脑图。”

## 5) 常见问题

1. **没有自动拉起 XMind**
   - 先确认 `.xmind` 双击能否在系统里正常打开；
   - 若不能，配置 `XMIND_EXECUTABLE` 为 `Xmind.exe` 的绝对路径。

2. **文件生成了但路径不对**
   - 未指定 `outputPath` 时，默认输出到 `Documents/xmind-mcp/`。
   - 可直接传绝对路径，例如：`C:\\Users\\you\\Desktop\\demo.xmind`。
