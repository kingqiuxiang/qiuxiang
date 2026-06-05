# AI File Cleaner (IntelliJ IDEA Plugin)

一个用于保持本地项目干净的 IntelliJ IDEA 插件：

- 自动识别并清理临时文件（tmp/temp/bak/swp 等）
- 识别可能的 AI 生成无用文件并自动清理
- 对项目配置文件 / AI 配置文件进行归档管理（`./.idea-ai-cleaner/ignore`、`./.idea-ai-cleaner/exclude`）
- 对不可预测/可疑文件给出一键操作：转存到指定目录或一键删除
- 支持 OpenAI 兼容 API（Base URL + API Key）做辅助识别

## 功能说明

### 1) 自动扫描时机

- 项目打开（导入）后自动扫描一次
- 文件创建/修改后进行增量清理
- 可通过 `Tools -> AI File Cleaner: Scan and Clean Project` 手动触发

### 2) 分类与处理策略

- **tmp 文件**：默认自动删除
- **AI 生成无用文件**：默认自动删除（规则 + 可选 API 辅助识别）
- **项目配置文件**：移动到 `./.idea-ai-cleaner/ignore`（关键目录仅登记到 manifest，避免破坏工程）
- **AI 配置文件**：移动到 `./.idea-ai-cleaner/exclude`（关键目录仅登记到 manifest）
- **可疑文件**：提示一键转存或一键删除

> 关键目录（如 `.idea/`, `.vscode/`, `.cursor/`）默认不会强制移动，只会记录到 `manifest.txt`。

## 设置项

`Settings/Preferences -> AI File Cleaner`

- `Base URL (OpenAI兼容)`
- `API Key`
- `启用 API 辅助识别 AI 文件`
- `自动删除 tmp/temp/临时文件`
- `自动删除识别出的 AI 生成无用文件`
- `可疑文件一键转存目录`

## 开发与运行

```bash
cd idea-ai-cleaner
./gradlew runIde
```

如果本地没有 `gradle wrapper`，先使用本机 Gradle 生成：

```bash
gradle wrapper
```

然后再运行 `./gradlew runIde`。
