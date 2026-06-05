# XMind MCP Server for Cursor

这个 MCP 服务让 Cursor 可以按你的需求生成 `.xmind` 思维导图文件，并在本机启动 XMind 打开它。它主要面向 Windows 本地环境，也兼容 macOS/Linux 的默认文件打开方式。

## 能力

- `create_xmind_map`：根据结构化主题树生成 XMind 文件。
- `create_xmind_from_outline`：根据 Markdown 标题/列表大纲生成 XMind 文件。
- `open_xmind_file`：打开已有 `.xmind` 文件。

Cursor 负责把你的自然语言需求整理成主题树或 Markdown 大纲，MCP 负责落盘为 `.xmind` 并拉起本地 XMind。

## Windows 本地安装

环境要求：

- Windows 10/11
- Node.js 18 或更高版本
- 已安装 XMind，并建议把 `.xmind` 文件默认打开程序设置为 XMind

在 PowerShell 中执行：

```powershell
cd C:\path\to\your\repo\mcp\xmind
npm install
npm run build
```

## Cursor MCP 配置

在 Cursor 中打开 MCP 配置文件，加入下面的配置。请把路径改成你自己电脑上的绝对路径。

```json
{
  "mcpServers": {
    "xmind": {
      "command": "node",
      "args": [
        "C:\\path\\to\\your\\repo\\mcp\\xmind\\dist\\index.js"
      ],
      "env": {
        "XMIND_OUTPUT_DIR": "C:\\Users\\YOUR_NAME\\Documents\\Cursor-XMind",
        "XMIND_EXE": "C:\\Users\\YOUR_NAME\\AppData\\Local\\Programs\\Xmind\\Xmind.exe"
      }
    }
  }
}
```

说明：

- `XMIND_OUTPUT_DIR` 可选，默认是 `%USERPROFILE%\Documents\Cursor-XMind`。
- `XMIND_EXE` 可选。如果不配置，服务会用 Windows 的 `.xmind` 默认打开程序启动 XMind。
- 常见 XMind 路径可能是：
  - `C:\Users\YOUR_NAME\AppData\Local\Programs\Xmind\Xmind.exe`
  - `C:\Program Files\Xmind\Xmind.exe`

如果你不确定 XMind 安装路径，可以先不配置 `XMIND_EXE`，只要 `.xmind` 已关联到 XMind，生成后也会自动打开。

## 在 Cursor 中使用

配置完成并重启/刷新 Cursor MCP 后，可以直接对 Cursor 说：

```text
用 XMind 帮我画一张「电商订单系统」的业务流程思维导图，包含下单、支付、库存、发货、售后，每个模块列出关键步骤。
```

或：

```text
把下面需求整理成 XMind 思维导图并打开：
...
```

Cursor 会调用 `create_xmind_map` 或 `create_xmind_from_outline`，生成文件并打开本地 XMind。

## 手动调试

构建后可以用 MCP Inspector 或 Cursor 调试该服务：

```powershell
cd C:\path\to\your\repo\mcp\xmind
npm run build
node .\dist\index.js
```

`node .\dist\index.js` 是 stdio MCP 服务，直接运行时会等待 MCP 客户端输入，不会显示普通 HTTP 服务地址。

## 工具参数简表

### create_xmind_from_outline

适合让 Cursor 先产出 Markdown 大纲：

```json
{
  "title": "电商订单系统",
  "outline": "# 电商订单系统\n- 下单\n  - 选择商品\n  - 提交订单\n- 支付\n  - 发起支付\n  - 支付回调",
  "openInXMind": true
}
```

### create_xmind_map

适合结构化主题树：

```json
{
  "title": "电商订单系统",
  "rootTopic": {
    "title": "电商订单系统",
    "children": [
      {
        "title": "下单",
        "children": [
          { "title": "选择商品" },
          { "title": "提交订单" }
        ]
      }
    ]
  },
  "openInXMind": true
}
```
