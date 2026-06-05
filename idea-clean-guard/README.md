# Clean Guard - IntelliJ IDEA Plugin

导入文件后自动识别 AI 生成文件、临时文件与配置文件，保持本地工作区干净。

## 功能

| 能力 | 说明 |
| --- | --- |
| **规则识别** | 临时文件（`.tmp`、`.bak`、`~` 等）、AI 配置（`.cursor/`、`AGENTS.md` 等）、项目配置（`.env`、`local.properties` 等） |
| **AI 深度检测** | 配置 OpenAI 兼容 API（API Key + Base URL + Model），对可疑文件做语义分类 |
| **自动清理** | 无用 AI 文件、临时文件导入后自动删除 |
| **自动 Exclude** | AI/项目配置文件自动写入 `.gitignore` 并标记 IDE Exclude |
| **手动处置** | 工具窗口 / 右键菜单：一键转存隔离区、一键删除、加入 ignore |
| **实时监控** | 项目打开后监听 VFS 变更，新导入文件即时处理 |

## 配置

**Settings → Tools → Clean Guard**

- **API Key** / **Base URL** / **Model**：OpenAI 兼容接口（DeepSeek、通义千问、Moonshot 等均可）
- **隔离目录**：可疑文件转存位置，默认 `.clean-guard/quarantine`
- 开关：自动清理临时文件、自动清理 AI 文件、自动 exclude 配置、启用 AI 检测

## 构建与安装

```bash
cd idea-clean-guard
./gradlew buildPlugin
```

产物位于 `build/distributions/idea-clean-guard-1.0.0.zip`。

在 IDEA 中：**Settings → Plugins → ⚙ → Install Plugin from Disk…** 选择 zip 安装。

## 使用

1. 打开项目后插件自动启动监控
2. 底部 **Clean Guard** 工具窗口查看扫描结果
3. **Tools → 扫描项目** 全量扫描
4. 编辑器右键 **Clean Guard** 对当前文件操作

## 技术栈

- Kotlin · IntelliJ Platform SDK 2024.1
- OkHttp · Gson（AI API 调用）
