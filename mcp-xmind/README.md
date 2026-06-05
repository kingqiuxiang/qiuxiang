# Cursor Xmind MCP（Windows）

一个可在 Cursor 里直接调用的本地 MCP 服务：

- 根据需求文本生成脑图结构；
- 产出 `.xmind` 文件；
- 自动拉起本地 Xmind 打开文件（Windows）。

## 1) 安装

```bash
cd mcp-xmind
npm install
```

## 2) 在 Cursor 配置 MCP

在 Windows 上编辑你的 Cursor MCP 配置（通常是 `%USERPROFILE%\.cursor\mcp.json`），加入：

```json
{
  "mcpServers": {
    "xmind-local": {
      "command": "node",
      "args": ["D:\\your-path\\workspace\\mcp-xmind\\src\\index.js"],
      "env": {
        "XMIND_EXE": "C:\\Program Files\\Xmind\\Xmind.exe"
      }
    }
  }
}
```

> `XMIND_EXE` 可选。不填时会尝试按系统文件关联打开 `.xmind` 文件。

## 3) 可用工具

### `xmind_generate_from_requirement`
- 输入 `requirement`（自然语言需求）
- 可选 `title`
- 自动生成 `.xmind` 并默认打开

### `xmind_generate_from_markdown`
- 输入 `markdownTree`（列表层级）
- 结构更可控，推荐用于精确脑图

示例：

```markdown
- 用户系统
  - 登录
  - 注册
- 订单系统
  - 下单
  - 支付
  - 退款
```

### `xmind_generate_from_json_tree`
- 输入 JSON 树（最精确，便于程序化）

### `xmind_open_file`
- 打开已有 `.xmind` 文件

## 4) 在 Cursor 里的建议提问方式

你可以直接对 Cursor 说：

1. `请调用 xmind_generate_from_requirement，根据以下需求画一张脑图：...`
2. `请先把需求整理成 markdown 树，再调用 xmind_generate_from_markdown 生成并打开 Xmind`

第二种方式结构通常更稳定。

## 5) 常见问题

- **没有自动打开 Xmind**
  - 确认本机已安装 Xmind；
  - 显式设置 `XMIND_EXE` 为 `Xmind.exe` 绝对路径；
  - 在 Cursor 内重载 MCP 配置。

- **节点层级不理想**
  - 优先使用 `xmind_generate_from_markdown` 或 `xmind_generate_from_json_tree`，避免仅靠自由文本推断结构。
