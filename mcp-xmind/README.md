# XMind MCP · 让 Cursor 拉起本地 XMind 自动画图

一个基于 [MCP（Model Context Protocol）](https://modelcontextprotocol.io) 的本地服务：在 **Cursor** 里直接对话提需求，AI 会把需求整理成层级结构，生成 `.xmind` 思维导图文件，并**自动用你本地的 XMind 打开**。

- ✅ 面向 **Windows**（同时兼容 macOS / Linux）
- ✅ 生成的是**现代 XMind 原生格式**（XMind 2020 及以后版本，可直接编辑）
- ✅ 支持结构化主题树、Markdown/缩进大纲两种输入
- ✅ 支持图标 marker、备注、标签、超链接、多种画布结构（思维导图/逻辑图/树状/组织结构/鱼骨图）

---

## 一、环境要求

- **Node.js ≥ 18**（在终端执行 `node -v` 确认。没有就到 https://nodejs.org 安装 LTS）
- **本地已安装 XMind**（XMind 2020 / 2021 / 2022 / 2023 / 2024 任一版本均可）

> 旧版 XMind 8 不支持新格式，请使用 XMind 2020 及以后版本。

## 二、安装

```bash
# 进入本目录
cd mcp-xmind

# 安装依赖
npm install

# （可选）跑一遍冒烟测试，确认一切正常
npm run smoke
```

## 三、在 Cursor 中配置 MCP

在 Cursor 中打开 **Settings → MCP → Add new global MCP server**（或直接编辑配置文件）：

- Windows 全局配置文件：`C:\Users\你的用户名\.cursor\mcp.json`
- 或项目级：在项目根目录新建 `.cursor\mcp.json`

填入以下内容（把路径换成你机器上 `mcp-xmind\src\index.js` 的**绝对路径**，注意 Windows 下反斜杠要写成 `\\`）：

```json
{
  "mcpServers": {
    "xmind": {
      "command": "node",
      "args": ["C:\\Users\\you\\code\\repo\\mcp-xmind\\src\\index.js"]
    }
  }
}
```

> 也可参考本目录的 `mcp.example.json`。

保存后回到 Cursor，在 MCP 设置里应能看到 `xmind` 服务为绿色/已连接，并列出 4 个工具：
`create_mindmap`、`create_mindmap_from_outline`、`open_xmind`、`xmind_env_info`。

## 四、开始使用（直接对 Cursor 说话）

配置好后，在 Cursor 对话框（Agent 模式）里直接用自然语言提需求即可，例如：

- 「用 xmind 帮我画一张『电商系统』的思维导图，包含用户、商品、订单、支付四大模块，每个模块再细分子功能。」
- 「把下面这段需求整理成 XMind 导图并打开：……」
- 「用鱼骨图分析『线上故障』的可能原因。」

Cursor 会自动调用本服务：在你**桌面**生成 `.xmind` 文件（默认以中心主题命名），并用本地 XMind 打开。

## 五、工具说明

### 1) `create_mindmap`（结构化）

| 参数 | 说明 |
| --- | --- |
| `title` | 中心主题（必填） |
| `children` | 子主题树，可无限嵌套；每个节点支持 `title / note / labels / markers / href / children` |
| `structure` | 画布结构，见下表（默认 `map`） |
| `sheetTitle` | 画布名（默认取中心主题） |
| `outputPath` | 输出路径（绝对/相对）；省略则存到桌面 |
| `open` | 是否自动打开，默认 `true` |
| `xmindPath` | XMind 可执行文件路径（无文件关联时用） |

### 2) `create_mindmap_from_outline`（大纲/Markdown）

把一段 Markdown 标题(`#`)或缩进列表(`-`/缩进) 自动解析为层级。第一行为中心主题。

```text
# 项目计划
## 需求阶段
- 调研
- 评审
## 开发阶段
- 后端
  - 接口设计
- 前端
```

### 3) `open_xmind`

用本地 XMind 打开一个已存在的 `.xmind` 文件；不传 `path` 时仅拉起 XMind 应用。

### 4) `xmind_env_info`

返回平台、默认输出目录等信息，便于排查路径问题。

## 六、画布结构类型（`structure`）

| 取值 | 含义 |
| --- | --- |
| `map` / `思维导图` | 思维导图（默认，左右平衡分布） |
| `balanced` | 平衡导图 |
| `logic_right` / `逻辑图` / `logic_left` | 逻辑图 |
| `tree_right` / `tree_left` | 树状图 |
| `org_down` / `组织结构` / `org_up` | 组织结构图 |
| `fishbone` / `鱼骨图` | 鱼骨图 |

## 七、图标 marker 别名

`markers` 既可传 XMind 原生 markerId（如 `priority-1`、`smiley-smile`、`flag-red`、`star-yellow`），也可用以下别名：

| 别名 | 效果 |
| --- | --- |
| `优先级1`~`优先级3` / `1`~`6` | 优先级标记 |
| `开始`/`start`、`完成`/`done`、`1/4`、`1/2`、`3/4` | 任务进度 |
| `smile`/`laugh`/`cry`/`angry` | 表情 |
| `flag`/`红旗`、`绿旗`、`star`/`星` | 旗帜 / 星标 |
| `yes`/`check`、`no`、`question`/`问号`、`感叹`、`info`、`plus`、`minus` | 符号 |

## 八、常见问题

- **生成了但没自动打开？** 多半是 `.xmind` 没有关联到 XMind。可手动双击文件，或在工具参数里加 `xmindPath`（如 `C:\\Users\\you\\AppData\\Local\\Programs\\XMind\\XMind.exe`）。也可调用 `open_xmind` 单独打开。
- **Cursor 里看不到工具 / 服务红色？** 确认 `node -v` ≥ 18、`mcp.json` 里的路径正确且用了 `\\`、并已 `npm install`。
- **文件存到哪了？** 默认桌面（找不到桌面时退到用户主目录 / 临时目录）。用 `xmind_env_info` 可查看「默认输出目录」。
- **想保存到指定位置？** 在需求里说明，或工具会用 `outputPath` 参数（绝对路径最稳）。

## 九、命令行直接测试（可选，验证不依赖 Cursor）

```bash
node -e "import('./src/xmind.js').then(async m=>{const b=await m.generateXmindBuffer({title:'测试',children:[{title:'A'}]});require('fs').writeFileSync('test.xmind',b);console.log('written test.xmind')})"
```

然后双击生成的 `test.xmind` 看能否被 XMind 打开。
