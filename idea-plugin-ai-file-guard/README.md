# AI File Guard / AI 文件卫士

An IntelliJ IDEA plugin that **detects AI-generated files and keeps your project directory clean**.

> 识别工程里"是不是 AI 生成的文件 / 临时文件 / 配置文件"，导入项目后及时清理无用文件、把配置类文件加入忽略、对可疑文件支持一键转存或一键删除。

---

## ✨ Features / 功能

| Category 分类 | What it is | Action 处理方式 |
| --- | --- | --- |
| **AI generated (useless)** AI 生成的无用文件 | Throwaway content produced by AI assistants | **Delete** 删除 |
| **Temporary** 临时文件 (`*.tmp`, `*.bak`, caches, build leftovers …) | tmp / cache / build leftovers | **Delete** 删除 |
| **Project config** 项目配置文件 (`.env`, `*.local`, `*.iml` …) | Real config that must be kept | **Add to ignore/exclude** 加入 `.gitignore` / git exclude |
| **AI config** AI 配置文件 (`.cursor/`, `.cursorrules`, `CLAUDE.md`, `AGENTS.md`, `.continue/` …) | Config for AI tools | **Add to ignore/exclude** 加入忽略 |
| **Suspicious / unknown** 可疑/不可预测文件 | Cannot be classified confidently | **One-click quarantine** 一键转存到指定目录 **or delete** 或一键删除 |

- **Configurable API Key & Base URL** — any OpenAI-compatible endpoint (OpenAI / DeepSeek / Qwen / Moonshot …) is used to classify files the local rules can't decide on. 支持配置 API Key 与 Base URL。
- **Scan on open / import** — automatically inspects the project when it is opened and notifies you. 导入/打开项目后自动扫描并提示。
- **Real-time new-file watch** — flags newly created/imported junk files immediately. 实时监控新增文件。
- **Tool window** with one-click *Clean (apply recommended)*, *Delete*, *Quarantine*, *Add to ignore*. 提供工具窗口，一键清理。

## 🧭 How it works / 工作原理

```
Scan project tree
      │
      ▼
Rule classifier (offline, fast)  ──► matched? ──► category + suggested action
      │ not confident / suspicious
      ▼
AI classifier (your API Key + Base URL)  ──► category + action + reason
      │
      ▼
Tool window  ──►  Delete / Quarantine / Add to ignore / Keep
```

The local rule classifier runs first (free, instant). Only files it cannot classify
confidently (and that are small enough text files) are sent to the AI endpoint.

## ⚙️ Configuration / 配置

`Settings / Preferences → Tools → AI File Guard`:

- **Base URL** — e.g. `https://api.openai.com/v1`
- **API Key** — your key (stored locally)
- **Model** — e.g. `gpt-4o-mini`
- **Quarantine directory** — where suspicious files are moved on "Quarantine"
- **Behaviour** — scan on open, watch new files, auto-apply safe actions
- **Classification patterns** — fully editable glob lists for each category (supports `*`, `?`, `**`)

## 🚀 Build & Run / 构建与运行

Requirements: JDK 21.

```bash
# Build the distributable plugin zip → build/distributions/ai-file-guard-1.0.0.zip
./gradlew buildPlugin

# Launch a sandbox IDE with the plugin installed
./gradlew runIde
```

Install the resulting zip via `Settings → Plugins → ⚙ → Install Plugin from Disk…`.

## 🧩 Usage / 使用

1. Open the **AI File Guard** tool window (right side bar) or run `Tools → AI File Guard: Scan Project`.
2. Click **Scan**. Review the table of flagged files (path, category, suggested action, confidence, reason).
3. Either:
   - Click **Clean (apply recommended)** to apply every suggested action at once, or
   - Select rows and click **Delete**, **Quarantine**, or **Add to ignore**.
4. Double-click a row to open the file in the editor before deciding.

## 🔒 Notes / 说明

- The API key is stored locally in the IDE settings; file contents are only sent to the
  endpoint **you** configure, and only for files the local rules cannot classify.
- Delete moves files through the IDE VFS; Quarantine relocates files to your configured
  directory (preserving relative paths). Both are explicit / confirmed actions.

## 📂 Project layout

```
idea-plugin-ai-file-guard/
├── build.gradle.kts                 # IntelliJ Platform Gradle Plugin 2.x
├── src/main/resources/META-INF/plugin.xml
└── src/main/kotlin/com/aifileguard/
    ├── model/Models.kt              # FileCategory / SuggestedAction / FileVerdict
    ├── settings/                    # persisted settings + Settings UI
    ├── classify/                    # PatternMatcher, RuleClassifier, AiClassifier, FileGuardScanner
    ├── action/                      # ScanAction, FileActionExecutor
    ├── toolwindow/                  # tool window factory, panel, table model
    ├── startup/                     # scan-on-open activity + new-file watcher
    └── util/Notifier.kt
```
