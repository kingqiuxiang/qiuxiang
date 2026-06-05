# XMind MCP for Cursor

这个 MCP 服务让 Cursor 可以根据你的需求生成本地 `.xmind` 文件，并在 Windows 上自动拉起本机 XMind 打开它。

## 能力

- `create_xmind`：根据结构化 JSON 主题树生成 XMind 文件。
- `create_xmind_from_markdown`：根据 Markdown 标题/列表大纲生成 XMind 文件。
- 默认输出到 `~/Documents/Cursor-XMind`。
- 支持通过 `XMIND_PATH` 指定 XMind 安装路径，或使用 Windows 默认文件关联打开 `.xmind`。

> MCP 负责创建并打开 XMind 文件；具体内容由 Cursor 根据你的自然语言需求整理成主题树或 Markdown 大纲后调用工具生成。

## Windows 使用步骤

### 1. 安装 Node.js

安装 Node.js 18 或更高版本，并确认 PowerShell / CMD 可执行：

```powershell
node -v
npm -v
```

### 2. 安装依赖

在本目录运行：

```powershell
cd C:\path\to\your\repo\mcp\xmind
npm install
```

### 3. 配置 Cursor MCP

打开 Cursor 的 MCP 配置文件，加入下面内容。请把路径替换成你 Windows 本机上的真实路径。

```json
{
  "mcpServers": {
    "xmind": {
      "command": "node",
      "args": [
        "C:\\path\\to\\your\\repo\\mcp\\xmind\\src\\index.js"
      ],
      "env": {
        "XMIND_OUTPUT_DIR": "C:\\Users\\你的用户名\\Documents\\Cursor-XMind",
        "XMIND_PATH": "C:\\Program Files\\Xmind\\Xmind.exe"
      }
    }
  }
}
```

如果 `.xmind` 文件已经关联到 XMind，可以删除 `XMIND_PATH`，服务会使用 Windows 默认打开方式：

```json
{
  "mcpServers": {
    "xmind": {
      "command": "node",
      "args": [
        "C:\\path\\to\\your\\repo\\mcp\\xmind\\src\\index.js"
      ],
      "env": {
        "XMIND_OUTPUT_DIR": "C:\\Users\\你的用户名\\Documents\\Cursor-XMind"
      }
    }
  }
}
```

### 4. 重启 Cursor

重启 Cursor 后，在聊天里可以直接说：

> 用 XMind 画一张“AI 接口测试平台”的产品架构图，包含前端、后端、AI 服务、YAPI、测试执行器、测试报告。

Cursor 会调用 MCP 生成 `.xmind` 文件，并自动打开 XMind。

## 工具参数示例

### create_xmind

```json
{
  "title": "AI 接口测试平台",
  "topics": [
    {
      "title": "前端",
      "children": [
        { "title": "项目管理" },
        { "title": "接口库" },
        { "title": "测试历史" }
      ]
    },
    {
      "title": "后端",
      "children": [
        { "title": "YAPI 拉取" },
        { "title": "AI 填参" },
        { "title": "请求执行" }
      ]
    }
  ],
  "openInXmind": true
}
```

### create_xmind_from_markdown

```json
{
  "markdown": "# AI 接口测试平台\n- 前端\n  - 项目管理\n  - 接口库\n- 后端\n  - YAPI 拉取\n  - AI 填参\n  - 请求执行",
  "openInXmind": true
}
```

## 环境变量

| 变量 | 说明 |
| --- | --- |
| `XMIND_OUTPUT_DIR` | 生成 `.xmind` 文件的默认目录。 |
| `XMIND_PATH` | XMind 可执行文件路径。未设置时使用系统默认打开方式。 |

## 常见问题

### Cursor 提示找不到 `node`

把 Node.js 的安装目录加入 Windows `PATH`，或者把 MCP 配置里的 `command` 改成完整路径，例如：

```json
"command": "C:\\Program Files\\nodejs\\node.exe"
```

### XMind 没有自动打开

优先设置 `XMIND_PATH`。如果不设置，请确认 Windows 已经把 `.xmind` 文件关联到 XMind。

### 想固定输出目录

设置 `XMIND_OUTPUT_DIR`，或在工具参数里传入 `outputDirectory`。
