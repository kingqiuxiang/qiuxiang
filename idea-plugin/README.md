# AI File Janitor · AI 文件清理助手 (IntelliJ IDEA 插件)

一个用于 **识别 AI 生成文件并保持工作区干净** 的 IntelliJ IDEA 插件。支持配置任意 **OpenAI 兼容**的 `API Key` 与 `Base URL`，在你导入/新增文件后自动识别并处理：

- 🤖 **AI 生成的无用文件** 与 🗑️ **临时文件** → 及时清理（删除）
- ⚙️ **项目配置文件 / AI 工具配置文件** → 加入 `.gitignore` / 标记为 IDE `Excluded`
- ❓ **可疑 / 不可预测的文件** → 一键 **转存到指定隔离目录**，或一键 **删除**

> 未配置 API Key 时，插件仅使用**本地启发式规则**运行，**不会发起任何外部网络请求**。

---

## ✨ 功能特性

| 能力 | 说明 |
| --- | --- |
| 🔌 可配置 AI | OpenAI 兼容 `Base URL` + `API Key` + `Model`（OpenAI / DeepSeek / 通义千问 / Moonshot 等） |
| 🧠 双层识别 | 先用本地启发式规则秒判，再对拿不准的文件调用 AI 复核（可关闭） |
| 🧹 自动清理 | 临时文件 / AI 无用产物 → 删除；可开启「高置信度直接删除」 |
| 🙈 自动忽略 | 项目 / IDE / AI 工具配置 → 写入 `.gitignore` 并可标记为 Excluded |
| 🧳 一键隔离 | 可疑文件移动到 `quarantineDir`（带时间戳，保留相对路径，绝不覆盖） |
| 📥 导入监听 | 导入/新增文件后自动检测，弹出通知「查看并清理」 |
| 👀 可视化复核 | 表格列出每个文件的类别 / 建议操作 / 置信度 / 原因，可逐行调整再执行 |

## 🗂️ 文件分类

| 类别 | 默认操作 |
| --- | --- |
| `AI 生成·无用` | 删除 |
| `临时文件` | 删除 |
| `项目配置` | 加入 ignore / 标记 Excluded |
| `AI 配置` | 加入 ignore / 标记 Excluded |
| `可疑文件` | 转存隔离 |
| `保留` | 不处理 |

识别依据包括：文件名 / 后缀 / 所在目录（如 `.tmp`、`*.bak`、`__pycache__`、`.idea/`、`.cursor/`、`.cursorrules`、`CLAUDE.md`、`.aider*` 等），以及文件内容中的 AI 输出特征与占位/模板痕迹；不确定的再交给 AI 判定。

## 🚀 构建与安装

环境要求：JDK 17+。

```bash
cd idea-plugin

# 构建插件包（产物在 build/distributions/ai-file-janitor-<version>.zip）
./gradlew buildPlugin

# 在沙箱 IDE 中实时调试运行
./gradlew runIde
```

安装：IntelliJ IDEA → `Settings/Preferences` → `Plugins` → ⚙️ → `Install Plugin from Disk...` → 选择 `build/distributions/ai-file-janitor-*.zip`。

## ⚙️ 配置

`Settings/Preferences` → `Tools` → `AI File Janitor`：

| 配置项 | 说明 | 示例 |
| --- | --- | --- |
| Base URL | OpenAI 兼容接口地址 | `https://api.openai.com/v1` |
| API Key | 接口密钥（留空 = 仅启发式） | `sk-...` |
| Model | 模型名 | `gpt-4o-mini` / `deepseek-chat` |
| 启用 AI 辅助识别 | 关闭后只跑本地规则 | ✓ |
| 导入时自动扫描 | 新增文件后自动检测并通知 | ✓ |
| 高置信度直接删除 | 置信≥85% 的临时/无用文件自动删除 | ✗ |
| 写入 .gitignore | 配置类文件追加到 `.gitignore` | ✓ |
| 标记 Excluded | 配置类目录标记为 IDE Excluded | ✓ |
| 可疑文件转存目录 | 隔离目录（相对项目根或绝对路径） | `.ai-janitor/quarantine` |
| 自定义临时规则 | 每行一个 glob，命中即视为临时文件 | `*.generated.ts` |

## 🧭 使用方式

- **扫描整个项目**：菜单 `Tools` → `AI 文件清理：扫描并清理`。
- **扫描指定文件/目录**：在项目树中选中 → 右键 → `AI 文件清理：扫描并清理`。
- **一键隔离**：右键选中文件 → `AI 文件清理：转存到隔离目录`。
- **一键删除**：右键选中文件 → `AI 文件清理：一键删除`（带确认）。
- **导入后**：自动弹出通知，点击「查看并清理」打开复核对话框。

## 🔒 隐私说明

- API Key 仅保存在本机 IDE 配置（`aiFileJanitor.xml`）中。
- 仅在你启用 AI 且触发扫描时，才会把**文件路径 + 截断后的开头内容**发送到你配置的 `Base URL`，发送上限可在设置中调整。
- 删除为永久删除（走 VFS，可被本地历史 Local History 一定程度恢复）；不确定的文件建议优先「转存隔离」。

## 🏗️ 技术栈

- Kotlin · IntelliJ Platform Gradle Plugin 2.x · Gradle 8.10
- 仅依赖平台 SDK，零第三方运行时依赖（内置极简 JSON 解析）

## 📂 目录结构

```
idea-plugin/
├── build.gradle.kts / settings.gradle.kts / gradle.properties
└── src/main/
    ├── kotlin/com/lingce/aijanitor/
    │   ├── settings/      # 持久化设置 + 配置 UI
    │   ├── classify/      # 分类模型 + 启发式 + AI 识别
    │   ├── service/       # 扫描编排 / 文件操作 / ignore 管理
    │   ├── action/        # 扫描清理 / 隔离 / 删除 动作
    │   ├── listener/      # 导入监听
    │   ├── ui/            # 扫描结果对话框
    │   └── util/          # JSON / 通知
    └── resources/META-INF/plugin.xml
```
