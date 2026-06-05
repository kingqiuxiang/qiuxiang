# AI File Janitor / AI 文件清道夫（IntelliJ IDEA 插件）

一个 IntelliJ IDEA 插件：**识别项目里的 AI 生成文件 / 临时文件 / 配置文件 / 可疑文件，并一键清理，保证本地干净**。

> 适用于「我从别处导入一个项目」之后，快速把 AI 工具留下的垃圾、临时产物、散落的配置文件整理干净。

---

## ✨ 功能

| 类别 | 识别内容 | 默认处理 |
| --- | --- | --- |
| 🤖 **AI 生成·无用** | 内容含 `@generated` / `AI-generated` 等标记、`*.generated.*`、`untitled*`、`scratch_*`、AI 写完忘删的草稿 | **删除** |
| 🧹 **临时文件** | `*.tmp` / `*.bak` / `*.swp` / `*.log` / 缓存 / `.DS_Store` / `Thumbs.db` / `__pycache__` 等 | **删除** |
| ⚙️ **项目配置** | `tsconfig.json` / `.editorconfig` / `.eslintrc*` / `.prettierrc*` 等 | **移入 ignore/exclude 目录** |
| 🧠 **AI 配置** | `.cursorrules` / `CLAUDE.md` / `AGENTS.md` / `.aider*` / `.continue` / `.windsurfrules` 等 | **移入 ignore/exclude 目录** |
| ❓ **可疑/未知** | 无法识别用途、异常或未知类型的文件 | **一键转存到指定目录** 或 **一键删除** |

- **本地规则 + AI 双重识别**：先用内置启发式规则快速分类，再（可选）调用 **任意 OpenAI 兼容模型** 复核「是否 AI 生成 / 是否无用」，判断更准。
- **可配置 API Key 与 Base URL**：支持 OpenAI / DeepSeek / 通义千问 / Moonshot 等。Key 安全存于 IDE PasswordSafe。
- **移入 ignore/exclude**：配置文件会被移动到指定目录，并写入仓库本地的 **`.git/info/exclude`**（不追踪、不污染 `.gitignore`，避免频繁改动）+ 在 IDE 中标记为 *Excluded*，从此不再干扰索引与版本控制。非 Git 项目时才回退写入 `.gitignore`。
- **可疑文件二选一**：勾选后「一键转存」到隔离目录，或「一键删除」。
- **导入即提醒**：打开项目时弹出通知，一键发起扫描（可在设置关闭）。
- **安全**：所有清理都在工具窗口里以列表呈现，逐条可勾选、可改操作，执行前二次确认，且为可撤销（Undo）的写命令。

## 🧭 使用

1. 安装插件后，右侧工具栏出现 **AI File Janitor** 工具窗口。
2. 打开 `Settings → Tools → AI File Janitor`，填写 **Base URL / API Key / Model**（不填也能用，只用本地规则）。
3. 在工具窗口点击 **扫描项目**（或菜单 `Tools → AI 清理：扫描项目`）。
4. 在结果表里勾选文件、按需调整「操作」列，然后：
   - **应用所选操作**：按每行选择的操作执行；
   - **一键删除所选** / **一键转存所选**：对可疑/未知文件最方便。

## 🛠️ 开发与构建

环境要求：JDK 21。

```bash
cd idea-plugin

# 构建插件 zip（产物在 build/distributions/）
./gradlew buildPlugin

# 在沙箱 IDE 中运行调试
./gradlew runIde
```

构建产物：`build/distributions/ai-file-janitor-1.0.0.zip`，可在
`IDEA → Settings → Plugins → ⚙ → Install Plugin from Disk…` 安装。

## 🏗️ 技术栈

- Kotlin + IntelliJ Platform Gradle Plugin 2.x（目标 IDEA 2024.2+，`sinceBuild = 242`）
- 纯 `java.net.http.HttpClient` 调用 OpenAI 兼容接口（无额外网络依赖）
- VFS / `WriteCommandAction` 执行可撤销的文件操作；`ModuleRootModificationUtil` 标记 Excluded

## 📂 结构

```
idea-plugin/
├── build.gradle.kts
├── src/main/resources/META-INF/plugin.xml
└── src/main/kotlin/com/lingce/aijanitor/
    ├── model/        # 数据模型（类别、操作、扫描项）
    ├── settings/     # 设置（API Key/Base URL/规则）+ 配置界面
    ├── core/         # 扫描、启发式 + AI 分类、清理、控制器
    ├── ui/           # 工具窗口与结果表格
    ├── actions/      # Tools 菜单动作
    └── startup/      # 打开项目时的扫描提醒
```
