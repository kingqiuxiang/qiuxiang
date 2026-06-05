package io.lingce.ideaguardian.model

import java.nio.file.Path

enum class FileCategory {
    TMP,
    AI_USELESS,
    CONFIG_OR_AI_CONFIG,
    SUSPICIOUS,
    SAFE
}

enum class SuggestedAction {
    DELETE,
    ADD_TO_IGNORE_EXCLUDE,
    ARCHIVE_OR_DELETE,
    KEEP
}

data class ScanItem(
    val path: Path,
    val relativePath: String,
    val category: FileCategory,
    val suggestion: SuggestedAction,
    val reason: String
)
