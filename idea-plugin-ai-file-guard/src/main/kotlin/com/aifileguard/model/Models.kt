package com.aifileguard.model

/**
 * High level classification of a file discovered during a scan.
 */
enum class FileCategory(val display: String) {
    /** AI generated content that has no lasting value (scratch notes, throwaway demos, etc.). */
    AI_GENERATED_USELESS("AI generated (useless)"),

    /** Temporary / cache / build leftovers. */
    TEMPORARY("Temporary"),

    /** Real project configuration that should stay but be ignored by VCS. */
    PROJECT_CONFIG("Project config"),

    /** Config files produced by AI assistants / tooling. */
    AI_CONFIG("AI config"),

    /** Unexpected, cannot be classified confidently. Needs a human decision. */
    SUSPICIOUS("Suspicious / unknown"),

    /** Normal source file. Keep untouched. */
    NORMAL("Normal");
}

/**
 * The action AI File Guard recommends (and can execute) for a file.
 */
enum class SuggestedAction(val display: String) {
    /** Delete the file to keep the workspace clean. */
    DELETE("Delete"),

    /** Keep the file but add it to ignore/exclude (.gitignore + git exclude). */
    ADD_TO_IGNORE("Add to ignore/exclude"),

    /** Move the file to the configured quarantine directory. */
    QUARANTINE("Quarantine"),

    /** Leave the file as-is. */
    KEEP("Keep");
}

/**
 * Result of classifying a single file.
 */
data class FileVerdict(
    val relativePath: String,
    val absolutePath: String,
    val category: FileCategory,
    val action: SuggestedAction,
    val reason: String,
    val confidence: Double,
    val sizeBytes: Long,
    /** true when the verdict was produced by the LLM rather than local rules. */
    val byAi: Boolean = false,
)
