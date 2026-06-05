# Cursor XMind MCP

一个给 Cursor 使用的本地 MCP Server：Cursor 根据你的需求整理脑图结构，调用本服务生成 `.xmind` 文件，并在 Windows 上拉起本地 XMind 打开。

## 能力

- `create_mindmap`：生成 XMind 脑图文件，可自动打开 XMind。
- `open_xmind`：启动 XMind 或打开指定 `.xmind` 文件。
- `check_xmind_setup`：检查是否能找到本机 XMind，并返回配置建议。

> 说明：MCP Server 不内置大模型。自然语言需求由 Cursor 先理解并整理成主题树，再调用 `create_mindmap` 写入 XMind 文件。

## Windows 准备

1. 安装 Node.js 18 或更高版本。
2. 安装 XMind 桌面版。
3. 在本目录安装依赖并构建：

```powershell
cd D:\path\to\your\repo\xmind-mcp
npm install
npm run build
```

如果 XMind 安装在非常规位置，记下 `XMind.exe` 的绝对路径，例如：

```text
C:\Program Files\Xmind\Xmind.exe
```

## Cursor MCP 配置

在 Cursor 的 MCP 配置中加入：

```json
{
  "mcpServers": {
    "xmind": {
      "command": "npm",
      "args": [
        "--prefix",
        "D:\\path\\to\\your\\repo\\xmind-mcp",
        "run",
        "start"
      ],
      "env": {
        "XMIND_PATH": "C:\\Program Files\\Xmind\\Xmind.exe",
        "XMIND_OUTPUT_DIR": "%USERPROFILE%\\Documents\\Cursor-XMind"
      }
    }
  }
}
```

注意：

- 把 `D:\\path\\to\\your\\repo\\xmind-mcp` 改成你本地仓库的真实路径。
- `XMIND_PATH` 可选；如果不配置，本服务会尝试常见安装路径，找不到时用 Windows 文件关联打开 `.xmind`。
- `XMIND_OUTPUT_DIR` 可选；不配置时 Windows 默认输出到 `%USERPROFILE%\Documents\Cursor-XMind`。

配置完成后，在 Cursor 的 MCP 面板刷新/启用 `xmind`。

## 使用方式

可以直接在 Cursor 里说：

```text
用 XMind 画一张“订单退款流程”的脑图，包含用户提交、客服审核、原路退款、异常处理和风险点。
```

Cursor 应调用 `create_mindmap`，类似传入：

```json
{
  "title": "订单退款流程",
  "topics": [
    {
      "title": "用户提交",
      "children": [
        { "title": "选择订单" },
        { "title": "填写退款原因" },
        { "title": "提交凭证" }
      ]
    },
    {
      "title": "客服审核",
      "children": [
        { "title": "校验订单状态" },
        { "title": "判断是否符合退款规则" }
      ]
    }
  ],
  "open": true
}
```

也可以传 Markdown 大纲：

```json
{
  "title": "项目启动计划",
  "outline": "- 背景\n  - 目标\n  - 范围\n- 里程碑\n  - 需求评审\n  - 技术方案\n- 风险\n  - 资源不足\n  - 需求变更",
  "open": true
}
```

## 本地验证

```powershell
npm run typecheck
npm run build
npm run smoke
```

`npm run smoke` 会在 `xmind-mcp\tmp` 目录生成一个测试 `.xmind` 文件。
