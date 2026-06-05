# mcp-xmind · 让 Cursor 直接画 XMind 思维导图

一个 [MCP](https://modelcontextprotocol.io/)（Model Context Protocol）服务：让 **Cursor** 把你的需求/方案直接整理成 **XMind 思维导图（`.xmind`）文件**，并**自动用你本地安装的 XMind 打开**。

> 适用：Windows（重点支持）/ macOS / Linux。本文以 **Windows** 环境为例。

---

## ✨ 能做什么

- 在 Cursor 对话里说一句「帮我把这个需求画成思维导图」，AI 调用本服务生成 `.xmind` 并自动拉起本地 XMind。
- 支持 4 种结构：思维导图 `map`、逻辑图 `logic`、组织结构图 `org`、树形图 `tree`。
- 支持 **Markdown 大纲** 一键出图，也支持 **结构化 JSON 树**（可带节点备注）。
- 生成的是标准 `.xmind` 文件（zip + `content.json`），可被当前版本 XMind 直接打开/再编辑。

## 🧰 提供的工具（Tools）

| 工具 | 说明 |
| --- | --- |
| `create_mind_map` | 根据 **Markdown 缩进大纲** 生成 `.xmind` 并自动打开（最常用） |
| `create_mind_map_from_tree` | 根据 **JSON 节点树**（`{title, note?, children?[]}`）生成并打开 |
| `open_xmind` | 用本地 XMind 打开一个已存在的 `.xmind` 文件 |
| `xmind_info` | 查看当前输出目录 / XMind 路径 / 平台等配置 |

---

## 🚀 安装（Windows）

1. 安装 [Node.js](https://nodejs.org/) ≥ 18（建议 LTS）。命令行验证：

```bat
node -v
```

2. 安装本地 [XMind](https://xmind.app/) 桌面端，并确保 `.xmind` 文件默认就是用 XMind 打开（双击任意 `.xmind` 能打开即可）。

3. 拉取本仓库并安装依赖：

```bat
cd mcp-xmind
npm install
```

---

## ⚙️ 在 Cursor 中配置

Cursor 的 MCP 配置文件位置：

- 项目级：项目根目录下 `.cursor/mcp.json`
- 全局级：`C:\Users\<你的用户名>\.cursor\mcp.json`

把下面内容写进去（注意 Windows 路径里的反斜杠要写成 `\\`）：

```json
{
  "mcpServers": {
    "xmind": {
      "command": "node",
      "args": ["C:\\path\\to\\mcp-xmind\\src\\index.js"],
      "env": {
        "XMIND_OUTPUT_DIR": "C:\\Users\\你的用户名\\Desktop\\XMind-MCP"
      }
    }
  }
}
```

> 把 `C:\\path\\to\\mcp-xmind` 换成你本机仓库的实际路径。

配置完成后，到 Cursor 的 **Settings → MCP** 里确认 `xmind` 服务为绿色已连接，并能看到上面 4 个工具。

### 可选环境变量

| 变量 | 作用 | 默认 |
| --- | --- | --- |
| `XMIND_OUTPUT_DIR` | 生成文件的默认输出目录（未显式指定 `file_path` 时使用） | `%USERPROFILE%\XMind-MCP` |
| `XMIND_PATH` | 指定 XMind 可执行文件的完整路径（默认走系统“默认打开方式”） | 不设置 |

`XMIND_PATH` 示例（仅当默认关联打不开时才需要）：

```json
"env": {
  "XMIND_PATH": "C:\\Program Files\\XMind\\XMind.exe"
}
```

---

## 💬 在 Cursor 里怎么用

直接用自然语言下达需求即可，例如：

> 「帮我把『电商系统』的需求画成 XMind：包含用户模块（注册登录、个人中心）、商品模块（列表、详情）、订单模块（下单、支付：微信/支付宝），画完用本地 XMind 打开。」

Cursor 会自动调用 `create_mind_map`，背后等价于传入这样的大纲：

```markdown
# 电商系统
- 用户模块
  - 注册/登录
  - 个人中心
- 商品模块
  - 商品列表
  - 商品详情
- 订单模块
  - 下单
  - 支付
    - 微信支付
    - 支付宝
```

生成后会在 `XMIND_OUTPUT_DIR` 下得到一个 `.xmind` 文件，并自动用本地 XMind 弹开。

### 大纲规则

- 用 `#`/`##`/`###` 标题 **或** 缩进（2/4 空格、Tab 都可）表示层级，可混用。
- 顶层若只有 1 个节点，它就是 **中心主题**；若有多个，可通过 `title` 参数指定中心主题，多个顶层节点会挂在其下。
- 列表标记 `-` `*` `+` `1.` 都支持。

### 选择不同结构

在请求里说明即可，例如「用**组织结构图**画团队架构」会让 AI 传 `structure: "org"`。可选：`map`（默认）、`logic`、`org`、`tree`。

---

## 🔍 本地自测（不依赖 Cursor）

用官方 Inspector 可视化调试：

```bat
cd mcp-xmind
npm run inspect
```

在 Inspector 里调用 `create_mind_map`，传入一段大纲，即可看到生成结果与文件路径。

---

## 🛠️ 常见问题

- **生成了但没自动打开？** 确认 `.xmind` 默认关联到了 XMind；或在配置里设置 `XMIND_PATH` 指向 `XMind.exe`。也可以让 AI 调用 `open_xmind` 手动打开返回的路径。
- **Cursor 里看不到工具 / 服务红灯？** 检查 `args` 路径是否正确、`node -v` 是否可用、`npm install` 是否在 `mcp-xmind` 目录执行过。
- **想换输出目录？** 改 `XMIND_OUTPUT_DIR`，或在请求里直接给出 `file_path`（如 `D:\\maps\\需求.xmind`）。
- **找不到生成的文件？** 让 AI 调用 `xmind_info` 查看当前 `outputDir`。

## 📦 文件格式说明

`.xmind` 是一个 zip 容器，本服务写入：

- `content.json` —— 思维导图主体（sheet + rootTopic + children.attached）
- `metadata.json` —— 创建者信息
- `manifest.json` —— 条目清单

该结构与当前 XMind 桌面端使用的 Zen 格式一致，可被正常打开与继续编辑。
