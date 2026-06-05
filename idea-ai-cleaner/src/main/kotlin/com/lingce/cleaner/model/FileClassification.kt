package com.lingce.cleaner.model

enum class FileCategory {
    SAFE,
    TMP,
    AI_GENERATED,
    PROJECT_CONFIG,
    AI_CONFIG,
    SUSPICIOUS
}

data class FileClassification(
    val category: FileCategory,
    val reason: String,
    val confidence: Double = 1.0
)
