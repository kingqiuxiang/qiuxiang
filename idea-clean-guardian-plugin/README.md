# Clean Guardian IDEA Plugin

一个用于保持本地项目整洁的 IntelliJ IDEA 插件：

- 识别并自动清理 `tmp/临时文件`
- 识别 AI 生成的无用文件并自动清理（可配置）
- 发现项目配置文件 / AI 配置文件时，自动记录到：
  - `.clean-guardian/ignore/exclude/paths.txt`
- 发现可疑文件时，支持：
  - 一键转存到指定目录（隔离目录）
  - 一键删除

## 导入方式

1. 在 IDEA 中 `File -> Open` 打开 `idea-clean-guardian-plugin` 目录。
2. 等待 Gradle 同步完成。
3. 运行 `gradle buildPlugin`，会在 `build/distributions` 产出 zip。
4. 在目标 IDEA 中 `Settings -> Plugins -> ⚙ -> Install Plugin from Disk` 导入 zip。

## 配置项

`Settings -> Tools -> Clean Guardian`：

- `AI API Base URL`
- `AI API Key`
- `AI Model`
- `可疑文件转存目录`
- `最大扫描文件大小(KB)`
- `自动删除 tmp / 临时文件`
- `自动删除 AI 生成的无用文件`
- `项目打开时自动扫描`
- `实时监听新增/变更文件`

## 手动触发

`Tools -> Scan and Clean Project (Clean Guardian)`
