package com.lingce.aicleaner.model

/**
 * 文件分类。识别一个文件属于哪一类，从而决定如何处理。
 */
enum class FileCategory(val displayName: String) {
    /** AI 生成的、无用的临时产物（脚本、示例、草稿等）。建议删除。 */
    AI_GENERATED_USELESS("AI 生成的无用文件"),

    /** 临时文件 / 缓存 / 编辑器残留。建议删除。 */
    TMP("临时文件"),

    /** 项目配置文件。建议加入 ignore / exclude。 */
    PROJECT_CONFIG("项目配置文件"),

    /** AI 工具配置文件（.cursor / CLAUDE.md / .aider 等）。建议加入 ignore / exclude。 */
    AI_CONFIG("AI 配置文件"),

    /** 无法预测 / 可疑文件。需要人工决定（转存或删除）。 */
    SUSPICIOUS("可疑文件"),

    /** 正常文件，应保留。 */
    NORMAL("正常文件");

    /** 该分类对应的推荐操作。 */
    val recommendedAction: CleanAction
        get() = when (this) {
            AI_GENERATED_USELESS -> CleanAction.DELETE
            TMP -> CleanAction.DELETE
            PROJECT_CONFIG -> CleanAction.IGNORE_EXCLUDE
            AI_CONFIG -> CleanAction.IGNORE_EXCLUDE
            SUSPICIOUS -> CleanAction.ASK
            NORMAL -> CleanAction.KEEP
        }
}

/**
 * 对文件可以执行的清理操作。
 */
enum class CleanAction(val displayName: String) {
    /** 直接删除。 */
    DELETE("删除"),

    /** 加入 .gitignore，并在 IDE 中标记为 excluded（保证本地/git 干净）。 */
    IGNORE_EXCLUDE("加入忽略/排除"),

    /** 转存到隔离目录。 */
    QUARANTINE("转存隔离"),

    /** 需要人工决策（可疑文件：一键转存或一键删除）。 */
    ASK("人工决策"),

    /** 保留，不处理。 */
    KEEP("保留");
}
