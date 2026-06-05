package com.lingce.cleanguard.model

enum class FileCategory(val displayName: String, val autoAction: AutoAction) {
    TMP("临时文件", AutoAction.DELETE),
    AI_GENERATED_USELESS("AI 生成（无用）", AutoAction.DELETE),
    AI_CONFIG("AI 配置文件", AutoAction.EXCLUDE),
    PROJECT_CONFIG("项目配置文件", AutoAction.EXCLUDE),
    SUSPICIOUS("可疑文件", AutoAction.NONE),
    SAFE("安全", AutoAction.NONE),
    UNKNOWN("未分类", AutoAction.NONE);

    enum class AutoAction {
        DELETE, EXCLUDE, NONE
    }
}

data class ClassifiedFile(
    val path: String,
    val category: FileCategory,
    val reason: String,
    val confidence: Double = 1.0,
)
