# Cursor Xmind MCP

在 **Windows** 本地通过 **Cursor** 直接生成思维导图，并自动用 **Xmind** 打开。

## 功能

| 工具 | 说明 |
| --- | --- |
| `create_mind_map` | 根据主题树生成 `.xmind` 并自动打开 Xmind |
| `open_xmind` | 用 Xmind 打开已有文件 |
| `launch_xmind` | 仅启动 Xmind 应用 |
| `read_mind_map` | 读取导图并导出 Markdown 大纲 |
| `list_xmind_files` | 列出目录下的 `.xmind` 文件 |
| `xmind_status` | 检查安装路径与配置（排障） |

## 环境要求

- **Node.js ≥ 18**
- **Windows**（已针对 Windows 优化 Xmind 路径探测）
- 已安装 [XMind](https://www.xmind.net/) 桌面版

## 安装

```bash
cd mcp-xmind
npm install
npm run build
```

## 在 Cursor 中配置（Windows）

### 方式一：项目级配置（推荐）

在项目根目录创建 `.cursor/mcp.json`：

```json
{
  "mcpServers": {
    "xmind": {
      "command": "node",
      "args": ["C:\\你的路径\\mcp-xmind\\dist\\index.js"],
      "env": {
        "XMIND_OUTPUT_PATH": "C:\\Users\\你的用户名\\Documents\\XMind",
        "XMIND_AUTO_OPEN": "true"
      }
    }
  }
}
```

### 方式二：全局配置

编辑 `%USERPROFILE%\.cursor\mcp.json`，内容同上。

### 方式三：使用 npx（发布到 npm 后）

```json
{
  "mcpServers": {
    "xmind": {
      "command": "npx.cmd",
      "args": ["-y", "cursor-xmind-mcp"],
      "env": {
        "XMIND_OUTPUT_PATH": "C:\\Users\\你的用户名\\Documents\\XMind"
      }
    }
  }
}
```

> **Windows 注意**：`npx` 请写成 `npx.cmd`；路径中的反斜杠在 JSON 里要写成 `\\`。

配置完成后 **完全退出并重启 Cursor**，在 **Settings → MCP** 中确认 `xmind` 服务为绿色已连接。

## 环境变量

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| `XMIND_OUTPUT_PATH` | 导图默认保存目录 | `~/Documents/XMind` |
| `XMIND_AUTO_OPEN` | 生成后是否自动打开 | `true` |
| `XMIND_EXE_PATH` | 手动指定 Xmind.exe 路径 | 自动探测 |

Windows 自动探测路径包括：

- `C:\Program Files\XMind\XMind.exe`
- `C:\Program Files (x86)\XMind\XMind.exe`
- `%LOCALAPPDATA%\Programs\XMind\XMind.exe`
- `%LOCALAPPDATA%\XMind\app-*\XMind.exe`

## 在 Cursor 里怎么用

配置好后，在对话中直接描述需求即可，例如：

> 帮我画一张「电商系统架构」思维导图，包含用户端、订单服务、支付服务、库存服务，每个服务下再列 2-3 个核心模块，保存为 `ecommerce-arch`，并用 Xmind 打开。

Cursor 会调用 `create_mind_map` 工具，生成文件并拉起本地 Xmind。

### `create_mind_map` 参数示例

```json
{
  "title": "电商系统架构",
  "filename": "ecommerce-arch",
  "topics": [
    {
      "title": "用户端",
      "children": [
        { "title": "Web 商城" },
        { "title": "移动端 App" }
      ]
    },
    {
      "title": "订单服务",
      "children": [
        { "title": "下单" },
        { "title": "订单查询" }
      ]
    }
  ],
  "relationships": [
    { "title": "调用", "from": "用户端", "to": "订单服务" }
  ]
}
```

## 排障

1. 运行 `xmind_status` 工具，确认 Xmind 路径是否被正确识别。
2. 若自动打开失败，设置 `XMIND_EXE_PATH` 为 Xmind 安装路径，例如：
   ```
   C:\Program Files\XMind\XMind.exe
   ```
3. 修改 `mcp.json` 后必须 **重启 Cursor**。
4. 在 Cursor 输出面板选择 **MCP** 查看服务日志。

## 开发

```bash
npm run dev    # tsx 直接运行
npm run build  # 编译到 dist/
```

## License

MIT
