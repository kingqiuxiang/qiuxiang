package com.lingce.aijanitor.model

import com.intellij.openapi.vfs.VirtualFile

/** High level classification of a file found during a scan. */
enum class FileCategory(val display: String) {
    AI_JUNK("AI 生成·无用"),
    TEMP("临时文件"),
    PROJECT_CONFIG("项目配置"),
    AI_CONFIG("AI 配置"),
    SUSPICIOUS("可疑/未知"),
    NORMAL("正常");
}

/** What the user wants to do with a given file. */
enum class CleanupAction(val display: String) {
    KEEP("保留"),
    DELETE("删除"),
    ARCHIVE("转存到目录"),
    IGNORE("移入 .git/info/exclude");
}

/** Result of classifying a single file. */
data class Classification(
    val category: FileCategory,
    val reason: String,
    val action: CleanupAction,
    val selectedByDefault: Boolean,
)

/** A single row in the scan result table. */
data class ScanItem(
    val file: VirtualFile,
    val relativePath: String,
    var category: FileCategory,
    var reason: String,
    var action: CleanupAction,
    var selected: Boolean,
)
