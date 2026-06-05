# Cursor XMind MCP

Local MCP server that lets Cursor create `.xmind` mind-map files from requirements and open them with the XMind desktop app.

## What it does

- Exposes a `create_xmind_map` MCP tool.
- Accepts either a Markdown outline or structured topic nodes.
- Writes a modern XMind package (`content.json`, `metadata.json`, `manifest.json` in a `.xmind` ZIP).
- Opens the generated file in local XMind on Windows, macOS, or Linux.

Cursor still performs the reasoning step: ask Cursor to turn your requirement into a mind-map outline, then it can call this MCP tool to create and open the XMind file.

## Windows setup for Cursor

1. Install Node.js 18+.
2. Install XMind for Windows.
3. Build this package:

   ```powershell
   cd C:\path\to\repo\mcp-xmind
   npm install
   npm run build
   ```

4. Add this server in Cursor MCP settings. Replace the paths with your local paths:

   ```json
   {
     "mcpServers": {
       "xmind": {
         "command": "node",
         "args": ["C:\\path\\to\\repo\\mcp-xmind\\dist\\index.js"],
         "env": {
           "XMIND_EXE": "C:\\Program Files\\Xmind\\Xmind.exe"
         }
       }
     }
   }
   ```

If XMind is already associated with `.xmind` files in Windows, `XMIND_EXE` is optional.

## Cursor usage examples

After enabling the MCP server, ask Cursor:

```text
把下面需求整理成 XMind 思维导图并打开：
我要做一个会员系统，包含注册登录、会员等级、积分、优惠券、订单权益、后台配置和风控。
```

Cursor should call `create_xmind_map` with a generated outline. By default, output files are saved to:

```text
%USERPROFILE%\Documents\Cursor-XMind
```

You can also ask for a specific output path:

```text
生成 XMind 并保存到 D:\mindmaps\member-system.xmind，然后打开。
```

## Available MCP tools

### `create_xmind_map`

Creates a `.xmind` file and optionally opens it.

Inputs:

- `title` - root topic and sheet title.
- `outline` - Markdown headings or bullet list.
- `nodes` - structured topic tree. When provided, this takes precedence over `outline`.
- `outputPath` - optional output path. `.xmind` is appended if missing.
- `openAfterCreate` - defaults to `true`.
- `xmindExecutable` - optional path to `Xmind.exe`; otherwise uses `XMIND_EXE` or OS file association.

### `open_xmind_map`

Opens an existing `.xmind` file.

### `get_xmind_cursor_setup`

Returns the setup guide from inside Cursor.

## Development

```bash
npm install
npm run typecheck
npm test
```
