package com.lingce.aijanitor.classify

import com.intellij.openapi.vfs.VirtualFile

/**
 * Result of classifying a single file.
 *
 * @param category    high-level bucket the file falls into
 * @param action      recommended action (mutable so the UI can let the user override it)
 * @param confidence  0.0..1.0 confidence of the classifier
 * @param reason      short human-readable explanation
 * @param source      where the verdict came from ("heuristic" / "ai")
 */
data class ClassificationResult(
    val file: VirtualFile,
    val category: FileCategory,
    var action: RecommendedAction,
    val confidence: Double,
    val reason: String,
    val source: String,
) {
    val isActionable: Boolean
        get() = action != RecommendedAction.KEEP
}
