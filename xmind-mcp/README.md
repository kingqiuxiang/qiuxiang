# XMind MCP Server

在 **Cursor** 中通过 MCP 调用本地 **XMind**，根据对话需求自动生成思维导图并在 Windows 上打开。

## 功能

| 工具 | 说明 |
| --- | --- |
| `xmind_create_and_open` | 根据主题树创建 `.xmind` 并拉起本地 XMind（主工具） |
| `xmind_create_mindmap` | 仅创建文件，不打开 |
| `xmind_read_mindmap` | 读取已有思维导图结构 |
| `xmind_open_file` | 用 XMind 打开已有文件 |
| `xmind_list_files` | 列出目录下的 `.xmind` 文件 |
| `xmind_status` | 检查输出目录与 XMind 安装情况 |

## 环境要求

- **Node.js** ≥ 18
- **Windows**（已针对 Windows 优化；macOS / Linux 也可创建文件并用系统默认应用打开）
- 已安装 [XMind](https://xmind.com)

## 安装

```bash
cd xmind-mcp
npm install
npm run build
```

## 在 Cursor 中配置（Windows）

### 方式一：项目级配置（推荐）

在仓库根目录创建或编辑 `.cursor/mcp.json`：

```json
{
  "mcpServers": {
    "xmind": {
      "command": "cmd",
      "args": [
        "/c",
        "node",
        "C:\\你的路径\\xmind-mcp\\dist\\index.js"
      ],
      "env": {
        "XMIND_OUTPUT_DIR": "C:\\Users\\你的用户名\\Documents\\XMind",
        "XMIND_EXECUTABLE": "C:\\Program Files\\XMind\\XMind.exe"
      }
    }
  }
}
```

> Windows 上建议用 `cmd /c` 包装 `node`，可避免部分 STDIO 连接问题。路径请使用双反斜杠 `\\`。

### 方式二：全局配置

编辑 `%APPDATA%\\Cursor\\User\\mcp.json`（或 Cursor 设置 → MCP），内容同上。

### 环境变量

| 变量 | 说明 |
| --- | --- |
| `XMIND_OUTPUT_DIR` | 默认保存目录，未设置时为 `Documents/XMind` |
| `XMIND_EXECUTABLE` | XMind 可执行文件路径（可选，未设置时自动探测常见安装位置） |

配置完成后 **重启 Cursor**，在对话中输入 `@xmind` 或让 AI 调用 XMind 工具。

## 使用示例

在 Cursor 对话中可以直接说：

- 「帮我用 XMind 画一个微服务架构思维导图」
- 「创建一个项目排期脑图，中心主题是 Q3 发布，包含开发、测试、上线三个分支」
- 「读取 `C:\Users\me\Documents\XMind\plan.xmind` 并补充测试分支」

AI 会调用 `xmind_create_and_open`，生成文件后自动打开 XMind。

### 工具输入结构示例

```json
{
  "title": "微服务架构",
  "sheetTitle": "系统架构",
  "children": [
    {
      "title": "网关层",
      "children": [
        { "title": "API Gateway" },
        { "title": "鉴权" }
      ]
    },
    {
      "title": "业务服务",
      "note": "按领域拆分",
      "labels": ["核心"],
      "children": [
        { "title": "订单服务" },
        { "title": "用户服务" }
      ]
    }
  ],
  "fileName": "microservices.xmind"
}
```

## 手动测试

```bash
# 检查环境
node dist/index.js

# 另开终端用 MCP Inspector（可选）
npx @modelcontextprotocol/inspector node dist/index.js
```

## 工作原理

1. 按 XMind Zen 标准生成 `.xmind` 文件（ZIP + `content.json`）
2. 在 Windows 上优先调用 `XMind.exe` 打开文件；找不到时使用 `start` 走系统文件关联
3. 通过 MCP stdio 与 Cursor 通信

## 故障排查

| 现象 | 处理 |
| --- | --- |
| Cursor 里看不到 xmind 工具 | 检查 `mcp.json` JSON 语法、重启 Cursor、查看 MCP 日志 |
| 文件创建了但没打开 XMind | 设置 `XMIND_EXECUTABLE` 为完整路径 |
| 打开了错误的程序 | 在 Windows 中将 `.xmind` 默认应用设为 XMind |
| 中文路径异常 | 尽量使用英文文件名，或确保路径 UTF-8 编码正常 |

## License

MIT
