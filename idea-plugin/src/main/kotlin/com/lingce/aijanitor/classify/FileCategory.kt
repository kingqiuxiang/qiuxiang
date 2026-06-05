package com.lingce.aijanitor.classify

/**
 * High-level classification of a file discovered during a scan.
 */
enum class FileCategory(val display: String) {
    /** AI-generated content that has no value (scratch output, placeholder code). */
    AI_GENERATED_USELESS("AI 生成·无用"),

    /** Temporary / build / editor junk files. */
    TEMP("临时文件"),

    /** Local/IDE/project config that should not be committed. */
    PROJECT_CONFIG("项目配置"),

    /** AI tool config (Cursor / Copilot / Aider / Claude ...). */
    AI_CONFIG("AI 配置"),

    /** Unexpected / can't confidently classify => let the user decide. */
    SUSPICIOUS("可疑文件"),

    /** Normal source / asset that should be kept. */
    KEEP("保留"),
}

/**
 * The concrete action recommended for a classified file.
 */
enum class RecommendedAction(val display: String) {
    DELETE("删除"),
    IGNORE("加入 ignore/exclude"),
    QUARANTINE("转存隔离"),
    KEEP("保留"),
}

fun FileCategory.defaultAction(): RecommendedAction = when (this) {
    FileCategory.AI_GENERATED_USELESS -> RecommendedAction.DELETE
    FileCategory.TEMP -> RecommendedAction.DELETE
    FileCategory.PROJECT_CONFIG -> RecommendedAction.IGNORE
    FileCategory.AI_CONFIG -> RecommendedAction.IGNORE
    FileCategory.SUSPICIOUS -> RecommendedAction.QUARANTINE
    FileCategory.KEEP -> RecommendedAction.KEEP
}
