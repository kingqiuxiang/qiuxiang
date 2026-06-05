# AI File Guardian Cleaner (IntelliJ IDEA Plugin)

一个用于保持本地项目整洁的 IDEA 插件：

- 识别并清理临时文件（tmp/bak/log/cache 等）
- 识别「AI 生成且疑似无用」文件并提供批量删除
- 将项目配置文件 / AI 配置文件加入 `ignore/exclude` 管理
- 对可疑文件提供一键转存到指定目录或一键删除
- 支持配置 `API Key` 与 `Base URL`，用于远程辅助识别
- 项目导入后自动弹窗，可一键立即扫描清理

## 本地开发

```bash
cd idea-plugin
./gradlew runIde
```

## 使用

1. 在 IDEA 中打开插件设置：`Settings > Tools > AI File Guardian`
2. 填写 `API Key`、`Base URL`（可选）和可疑文件转存目录（可选）
3. 在菜单执行：`Tools > AI File Guardian: Scan & Clean`
4. 在扫描结果里：
   - 点击 **执行建议清理**（处理 tmp、AI 无用文件、ignore/exclude）
   - 点击 **一键转存可疑文件** 或 **一键删除可疑文件**

## ignore/exclude 输出

插件会在项目内维护：

- `ignore/exclude/managed-ignore-list.txt`
- `.git/info/exclude`（若存在则追加）
