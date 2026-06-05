# XMind Local MCP（Windows）

这个 MCP 服务让 Cursor 可以直接：

1. 根据需求文本生成 `.xmind` 脑图
2. 根据 Markdown 大纲生成 `.xmind` 脑图
3. 自动拉起你本地的 XMind 打开文件

## 1) 安装

```bash
cd mcp/xmind-local
npm install
```

## 2) 在 Cursor 中配置 MCP

在 **Windows 本地** 的 Cursor MCP 配置里加入：

```json
{
  "mcpServers": {
    "xmind-local": {
      "command": "node",
      "args": ["D:\\\\your-path\\\\mcp\\\\xmind-local\\\\src\\\\index.js"],
      "env": {
        "XMIND_OUTPUT_DIR": "D:\\\\xmind-output",
        "XMIND_EXE_PATH": "C:\\\\Program Files\\\\Xmind\\\\XMind.exe"
      }
    }
  }
}
```

说明：

- `args` 需要改成你自己机器上的绝对路径
- `XMIND_OUTPUT_DIR` 是生成脑图的目录（可选，不填默认 `Documents/xmind-maps`）
- `XMIND_EXE_PATH` 是 XMind 可执行文件路径（可选；不填时会用系统默认程序打开 `.xmind`）

## 3) 可用工具

### `create_xmind_from_requirement`

输入需求文本，自动拆分分支并生成脑图。

参数：

- `requirement` (string, 必填)
- `title` (string, 可选，默认：`需求脑图`)
- `file_name` (string, 可选)
- `auto_open` (boolean, 可选，默认：`true`)

### `create_xmind_from_outline`

输入 Markdown 大纲，按层级直接生成脑图。

参数：

- `title` (string, 必填)
- `outline_markdown` (string, 必填)
- `file_name` (string, 可选)
- `auto_open` (boolean, 可选，默认：`true`)

### `open_xmind_file`

打开已有脑图文件。

参数：

- `file_path` (string, 必填，绝对路径)

## 4) 推荐在 Cursor 中这样提需求

你可以直接说：

- “根据下面需求生成一张登录模块脑图并打开 XMind”
- “把这个大纲画成 XMind：...”

Cursor 会自动调用对应 MCP 工具生成并打开文件。
