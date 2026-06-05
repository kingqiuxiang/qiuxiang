# XMind MCP Server

在 **Cursor** 中通过 MCP 协议，根据自然语言需求自动生成 `.xmind` 思维导图，并在 **Windows 本地 XMind** 客户端中打开。

## 功能

| 工具 | 说明 |
| --- | --- |
| `xmind_draw` | **推荐** — 根据 Markdown 大纲或 JSON 树一键生成并打开 |
| `xmind_create` | 创建空白导图 |
| `xmind_add_node` / `xmind_add_nodes` | 添加节点 |
| `xmind_read` | 读取导图结构 |
| `xmind_update_node` / `xmind_delete_node` | 修改 / 删除节点 |
| `xmind_export` | 导出 Markdown / 文本 |
| `xmind_open` | 用本地 XMind 打开文件 |
| `xmind_status` | 检查环境与 XMind 路径 |

## 环境要求

- **Node.js ≥ 18**
- **Windows**（也支持 macOS / Linux 打开文件，但主要针对 Windows + XMind 桌面版优化）
- 已安装 [XMind 桌面版](https://www.xmind.net/download/)

## 安装

```powershell
# 克隆或进入本目录
cd xmind-mcp

# 安装依赖并编译
npm install
npm run build
```

## 在 Cursor 中配置（Windows）

1. 打开 Cursor → **Settings** → **MCP** → **Add new MCP server**
2. 或直接编辑配置文件：

**全局配置路径：** `%USERPROFILE%\.cursor\mcp.json`

```json
{
  "mcpServers": {
    "xmind": {
      "command": "node",
      "args": ["C:\\path\\to\\xmind-mcp\\dist\\index.js"],
      "env": {
        "XMIND_OUTPUT_DIR": "C:\\Users\\你的用户名\\Documents\\XMind",
        "XMIND_EXE_PATH": "C:\\Users\\你的用户名\\AppData\\Local\\Programs\\XMind\\XMind.exe"
      }
    }
  }
}
```

> **重要：** 请把 `args` 中的路径改成你机器上 `xmind-mcp/dist/index.js` 的**绝对路径**。

### 可选环境变量

| 变量 | 说明 | 示例 |
| --- | --- | --- |
| `XMIND_OUTPUT_DIR` | 默认输出目录 | `C:\Users\you\Documents\XMind` |
| `XMIND_EXE_PATH` | XMind 可执行文件路径（自动探测失败时设置） | `C:\Program Files\XMind\XMind.exe` |

### 项目级配置

也可在项目根目录创建 `.cursor/mcp.json`，仅当前项目生效。

## 使用示例

配置完成后，在 Cursor Agent 中直接说：

> 帮我画一个「电商系统架构」的思维导图，包含用户模块、订单模块、支付模块，每个模块下写 3 个子功能，生成后用 XMind 打开。

Agent 会调用 `xmind_draw`，传入类似下面的 Markdown：

```markdown
# 电商系统架构

## 用户模块
- 注册登录
- 个人中心
- 权限管理

## 订单模块
- 下单
- 订单查询
- 退款

## 支付模块
- 微信支付
- 支付宝
- 对账
```

## Markdown 大纲格式

- `#` 一级标题 → 中心主题（若与 `title` 参数重复，以文件内容为准）
- `##` / `###` → 分支层级
- `-` 列表项 → 子节点

## 开发

```bash
npm run dev    # 开发模式（tsx）
npm run build  # 编译到 dist/
npm start      # 运行 MCP 服务
```

## 工作原理

```
Cursor Agent
    ↓ MCP (stdio)
xmind-mcp Server
    ↓ xmindcli
生成 .xmind 文件
    ↓ start / XMind.exe
本地 XMind 打开编辑
```

底层使用 [xmindcli](https://www.npmjs.com/package/xmindcli) 读写 `.xmind` 文件，输出结构化 JSON，适合 AI 调用。

## 常见问题

**Q: Cursor 里看不到 MCP 工具？**  
A: 确认 `mcp.json` 路径正确、已 `npm run build`，并在 Cursor MCP 面板点击刷新。

**Q: 文件生成了但 XMind 没打开？**  
A: 设置 `XMIND_EXE_PATH` 为 XMind 安装路径，或手动调用 `xmind_open`。

**Q: 中文乱码？**  
A: 确保 Markdown 内容为 UTF-8，Windows 终端建议使用 UTF-8 代码页。

## License

MIT
