package com.lingce.cleankeeper.model

enum class FileCategory(val displayName: String, val description: String) {
    SAFE("安全", "正常项目文件，无需处理"),
    TMP_FILE("临时文件", "可安全删除的临时/缓存文件"),
    AI_GENERATED_USELESS("AI 无用产物", "AI 生成的草稿、示例或无用文件"),
    PROJECT_CONFIG("项目配置", "本地环境配置，建议忽略"),
    AI_CONFIG("AI 配置", "AI 工具配置文件，建议忽略"),
    SUSPICIOUS("可疑文件", "无法自动判定，需人工确认");

    fun shouldAutoDelete(): Boolean = this == TMP_FILE || this == AI_GENERATED_USELESS

    fun shouldAddToIgnore(): Boolean = this == PROJECT_CONFIG || this == AI_CONFIG
}

data class FileClassification(
    val category: FileCategory,
    val confidence: Double,
    val reason: String,
    val source: ClassificationSource,
)

enum class ClassificationSource {
    HEURISTIC,
    AI,
    MANUAL,
}
