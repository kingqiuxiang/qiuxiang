# AI File Cleaner (IntelliJ IDEA Plugin)

这个插件用于在导入项目后自动做文件卫生清理：

- 识别并清理 `tmp/temp/bak/swp` 等临时文件
- 识别并清理「疑似 AI 生成且无用」文件
- 将项目配置文件 / AI 配置文件移动到 `ignore/exclude` 目录（默认 `.project-cleaner/ignore-exclude`）
- 对不可预测或可疑文件提供一键转存 / 一键删除

## 功能概览

1. **自动扫描（项目打开后）**
   - 默认开启，可在设置中关闭。
2. **配置 API Key + Base URL**
   - 使用 OpenAI 兼容接口辅助判断 `SAFE / AI_JUNK / SUSPICIOUS`。
3. **可疑文件处置**
   - Tool Window 内可执行：
     - 一键转存到指定目录（默认 `.project-cleaner/suspicious-archive`）
     - 一键删除

## 设置项

`Settings/Preferences -> Tools -> AI File Cleaner`

- API Base URL
- API Key
- ignore/exclude 目录
- 可疑文件转存目录
- 最大扫描文件大小（KB）
- 是否自动扫描 / 自动删除 tmp / 自动删除 AI 垃圾

## 使用方式

1. 在 IDEA 打开 `idea-plugin` 子工程。
2. 使用 Gradle 的 `runIde` 启动沙箱 IDE（需要本机可用 Gradle 环境）。
3. 在沙箱 IDE 中打开目标项目，插件会自动扫描。
4. 也可手动执行 `Tools -> Run AI File Cleaner Scan`。
5. 打开右侧 `AI File Cleaner` Tool Window 查看结果并执行可疑文件一键操作。

## 说明

- 为避免误删，插件默认仅按启发式 + 可选模型判定 AI 垃圾文件。
- 插件会跳过 `.git`、`node_modules`、`build`、`out` 目录。
