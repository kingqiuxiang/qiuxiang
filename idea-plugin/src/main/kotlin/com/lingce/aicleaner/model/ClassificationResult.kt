package com.lingce.aicleaner.model

import com.intellij.openapi.vfs.VirtualFile

/**
 * 单个文件的分类结果。
 *
 * @param file        被分类的文件
 * @param category    分类
 * @param reason      分类依据（给用户看的可读说明）
 * @param confidence  置信度 0.0 ~ 1.0
 * @param byAi        是否由 AI 模型判定（false 表示由本地规则判定）
 */
data class ClassificationResult(
    val file: VirtualFile,
    val category: FileCategory,
    val reason: String,
    val confidence: Double,
    val byAi: Boolean = false,
) {
    val recommendedAction: CleanAction get() = category.recommendedAction

    /** 是否需要被处理（即非 NORMAL）。 */
    val actionable: Boolean get() = category != FileCategory.NORMAL
}
