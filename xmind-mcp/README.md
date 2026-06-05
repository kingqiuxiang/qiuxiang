# Cursor XMind MCP

在 **Cursor** 中通过 MCP 调用本地 **XMind**，根据需求描述自动生成思维导图并在 Windows 上打开。

## 功能

| 工具 | 说明 |
| --- | --- |
| `xmind_generate` | 根据结构化主题树生成 `.xmind` 并自动打开 |
| `xmind_generate_from_outline` | 从 Markdown/缩进大纲快速生成脑图 |
| `xmind_open` | 打开已有 `.xmind` 文件 |
| `xmind_launch` | 仅启动 XMind 桌面应用 |
| `xmind_read` | 读取现有脑图结构（返回大纲） |
| `xmind_get_config` | 查看输出目录、XMind 路径等配置 |

## 环境要求

- **Node.js** ≥ 18
- **XMind** 桌面版（[官网下载](https://www.xmind.cn/)）
- **Cursor**（支持 MCP 的版本）

## 安装

```powershell
# 进入本目录
cd xmind-mcp

# 安装依赖并编译
npm install
npm run build
```

编译后入口文件为：`xmind-mcp/dist/index.js`

## 在 Cursor 中配置（Windows）

### 方式一：项目级配置（推荐）

在项目根目录创建 `.cursor/mcp.json`：

```json
{
  "mcpServers": {
    "xmind": {
      "command": "node",
      "args": ["D:\\your-project\\xmind-mcp\\dist\\index.js"],
      "env": {
        "XMIND_OUTPUT_PATH": "D:\\Documents\\XmindFiles",
        "XMIND_AUTO_OPEN": "true",
        "XMIND_PATH": "C:\\Users\\你的用户名\\AppData\\Local\\Programs\\XMind\\XMind.exe"
      }
    }
  }
}
```

> 将 `args` 中的路径改为你本机 `xmind-mcp/dist/index.js` 的**绝对路径**。

### 方式二：全局配置

编辑 Cursor 全局 MCP 配置（Windows）：

`%USERPROFILE%\.cursor\mcp.json`

内容同上。

### 配置项说明

| 环境变量 | 说明 | 默认值 |
| --- | --- | --- |
| `XMIND_OUTPUT_PATH` | 生成文件的保存目录 | `%USERPROFILE%\Documents\XmindFiles` |
| `XMIND_AUTO_OPEN` | 生成后是否自动打开 XMind | `true` |
| `XMIND_PATH` | XMind.exe 完整路径（找不到时设置） | 自动搜索常见安装位置 |

### XMind 常见安装路径（Windows）

- `%LOCALAPPDATA%\Programs\XMind\XMind.exe`
- `C:\Program Files\XMind\XMind.exe`

## 使用示例

配置并重启 Cursor 后，在对话中直接描述需求，例如：

> 帮我画一个「电商系统架构」思维导图，包含：用户端、订单服务、支付服务、库存服务，每个服务下写 2-3 个核心模块。

Cursor 会调用 `xmind_generate` 或 `xmind_generate_from_outline` 生成文件，并自动用 XMind 打开。

### 大纲模式示例

也可以提供大纲让 AI 出图：

```markdown
- 项目计划
  - 需求分析
    - 用户访谈
    - 竞品分析
  - 技术方案
    - 前端 React
    - 后端 Node.js
  - 里程碑
    - MVP 上线
    - 正式发布
```

## 排查问题

1. 在 Cursor 中让 AI 调用 `xmind_get_config`，确认 XMind 路径和输出目录。
2. 若无法自动打开，手动设置 `XMIND_PATH` 为 `XMind.exe` 的完整路径。
3. 确认 Node.js 在系统 PATH 中：`node -v`
4. 修改 MCP 配置后需**重启 Cursor** 或重新加载 MCP 服务。

## 开发

```bash
npm run dev    # 开发模式（tsx）
npm run build  # 编译 TypeScript
npm start      # 运行 MCP 服务（stdio）
```

## 许可证

MIT
