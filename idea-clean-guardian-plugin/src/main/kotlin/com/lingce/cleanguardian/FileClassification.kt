package com.lingce.cleanguardian

enum class FileCategory {
    TMP,
    PROJECT_CONFIG,
    AI_CONFIG,
    AI_GARBAGE,
    SUSPICIOUS,
    NORMAL,
    SKIPPED
}

data class ClassificationResult(
    val category: FileCategory,
    val confidence: Double,
    val reason: String
)
