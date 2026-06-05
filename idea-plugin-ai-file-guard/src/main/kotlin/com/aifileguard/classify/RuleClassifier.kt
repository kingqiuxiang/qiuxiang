package com.aifileguard.classify

import com.aifileguard.model.FileCategory
import com.aifileguard.model.FileVerdict
import com.aifileguard.model.SuggestedAction
import com.aifileguard.settings.AiGuardSettings

/**
 * Fast, offline, pattern based classification. Always runs first.
 * When it cannot decide it returns a [FileCategory.SUSPICIOUS] verdict so the
 * caller may optionally escalate to the AI classifier.
 */
class RuleClassifier(private val settings: AiGuardSettings.State) {

    private val tmp = PatternMatcher.parsePatterns(settings.tmpPatterns)
    private val aiArtifacts = PatternMatcher.parsePatterns(settings.aiArtifactPatterns)
    private val projectConfig = PatternMatcher.parsePatterns(settings.projectConfigPatterns)
    private val aiConfig = PatternMatcher.parsePatterns(settings.aiConfigPatterns)

    fun classify(relativePath: String, fileName: String, sizeBytes: Long, absolutePath: String): FileVerdict {
        fun verdict(category: FileCategory, action: SuggestedAction, reason: String, confidence: Double) =
            FileVerdict(relativePath, absolutePath, category, action, reason, confidence, sizeBytes, byAi = false)

        if (PatternMatcher.matchesAny(aiArtifacts, relativePath, fileName)) {
            return verdict(
                FileCategory.AI_GENERATED_USELESS,
                SuggestedAction.DELETE,
                "Matches AI-artifact pattern",
                0.95,
            )
        }
        if (PatternMatcher.matchesAny(tmp, relativePath, fileName)) {
            return verdict(
                FileCategory.TEMPORARY,
                SuggestedAction.DELETE,
                "Matches temporary-file pattern",
                0.95,
            )
        }
        if (PatternMatcher.matchesAny(aiConfig, relativePath, fileName)) {
            return verdict(
                FileCategory.AI_CONFIG,
                SuggestedAction.ADD_TO_IGNORE,
                "Matches AI-config pattern",
                0.9,
            )
        }
        if (PatternMatcher.matchesAny(projectConfig, relativePath, fileName)) {
            return verdict(
                FileCategory.PROJECT_CONFIG,
                SuggestedAction.ADD_TO_IGNORE,
                "Matches project-config pattern",
                0.9,
            )
        }

        // Empty / zero byte files are almost always junk leftovers.
        if (sizeBytes == 0L) {
            return verdict(
                FileCategory.SUSPICIOUS,
                SuggestedAction.QUARANTINE,
                "Empty (0 byte) file",
                0.4,
            )
        }

        return verdict(
            FileCategory.NORMAL,
            SuggestedAction.KEEP,
            "No rule matched",
            0.2,
        )
    }
}
