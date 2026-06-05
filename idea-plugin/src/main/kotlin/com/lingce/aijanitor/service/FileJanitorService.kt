package com.lingce.aijanitor.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.lingce.aijanitor.classify.AiClassifier
import com.lingce.aijanitor.classify.ClassificationResult
import com.lingce.aijanitor.classify.FileCategory
import com.lingce.aijanitor.classify.HeuristicClassifier
import com.lingce.aijanitor.classify.RecommendedAction
import com.lingce.aijanitor.settings.AiJanitorSettings

/**
 * Project-level orchestrator: walks the requested files, classifies them, and
 * applies the chosen actions.
 */
@Service(Service.Level.PROJECT)
class FileJanitorService(private val project: Project) {

    private val log = logger<FileJanitorService>()

    // Directories we never want to descend into during a scan.
    private val ALWAYS_SKIP_DIRS = setOf(".git", ".hg", ".svn")

    fun scan(roots: List<VirtualFile>, indicator: ProgressIndicator?): List<ClassificationResult> {
        val settings = AiJanitorSettings.getInstance()
        val extraGlobs = settings.extraTempGlobs.lines().map { it.trim() }.filter { it.isNotBlank() }
        val ai = AiClassifier(settings)
        val quarantineAbs = quarantineRootPath(settings)

        val collected = LinkedHashMap<String, VirtualFile>()
        val results = mutableListOf<ClassificationResult>()

        // First pass: enumerate candidate files (and folded config/temp dirs).
        val flat = mutableListOf<VirtualFile>()
        for (root in roots) {
            walk(root, extraGlobs, quarantineAbs, flat, collected)
        }

        indicator?.isIndeterminate = false
        val total = flat.size.coerceAtLeast(1)
        flat.forEachIndexed { idx, file ->
            indicator?.checkCanceled()
            indicator?.fraction = idx.toDouble() / total
            indicator?.text2 = file.name

            var verdict = HeuristicClassifier.classify(file, extraGlobs)
            // Ask the AI for files the heuristics couldn't confidently bucket.
            if (ai.isAvailable() && shouldRefineWithAi(verdict)) {
                ai.classify(file, project.basePath)?.let { aiVerdict ->
                    verdict = mergeVerdicts(verdict, aiVerdict)
                }
            }
            results += verdict
        }
        return results
    }

    /** Apply the action stored on [result]; returns a human-readable status string. */
    fun apply(result: ClassificationResult): String {
        val settings = AiJanitorSettings.getInstance()
        val ops = FileOpsService(project)
        val ignore = IgnoreFileManager(project)
        val name = result.file.name
        return when (result.action) {
            RecommendedAction.KEEP -> "保留: $name"
            RecommendedAction.DELETE ->
                ops.delete(result.file).fold(
                    onSuccess = { "已删除: $name" },
                    onFailure = { "删除失败: $name (${it.message})" },
                )
            RecommendedAction.QUARANTINE ->
                ops.quarantine(result.file).fold(
                    onSuccess = { "已转存隔离: $name -> $it" },
                    onFailure = { "转存失败: $name (${it.message})" },
                )
            RecommendedAction.IGNORE -> {
                val outcome = ignore.ignore(result.file, settings.addToGitIgnore, settings.markExcluded)
                if (outcome.gitIgnored || outcome.excluded) "已忽略: $name (${outcome.detail})"
                else "忽略: $name (${outcome.detail})"
            }
        }
    }

    fun applyAll(results: List<ClassificationResult>, indicator: ProgressIndicator?): List<String> {
        val logs = mutableListOf<String>()
        val total = results.size.coerceAtLeast(1)
        results.forEachIndexed { idx, r ->
            indicator?.checkCanceled()
            indicator?.fraction = idx.toDouble() / total
            indicator?.text2 = r.file.name
            if (r.isActionable && r.file.isValid) {
                logs += apply(r)
            }
        }
        return logs
    }

    private fun walk(
        file: VirtualFile,
        extraGlobs: List<String>,
        quarantineAbs: String?,
        out: MutableList<VirtualFile>,
        seen: MutableMap<String, VirtualFile>,
    ) {
        if (!file.isValid) return
        if (seen.put(file.path, file) != null) return
        if (quarantineAbs != null && file.path.startsWith(quarantineAbs)) return

        if (file.isDirectory) {
            if (file.name in ALWAYS_SKIP_DIRS) return
            val dirVerdict = HeuristicClassifier.classify(file, extraGlobs)
            // A whole config/temp directory is handled as a single unit.
            if (dirVerdict.category in FOLDED_DIR_CATEGORIES) {
                out += file
                return
            }
            file.children?.forEach { walk(it, extraGlobs, quarantineAbs, out, seen) }
        } else {
            out += file
        }
    }

    private fun shouldRefineWithAi(verdict: ClassificationResult): Boolean {
        // Refine when we're unsure: KEEP/low confidence or explicitly SUSPICIOUS.
        return when (verdict.category) {
            FileCategory.KEEP -> verdict.confidence < 0.6
            FileCategory.SUSPICIOUS -> true
            else -> verdict.confidence < 0.6
        }
    }

    private fun mergeVerdicts(
        heuristic: ClassificationResult,
        ai: ClassificationResult,
    ): ClassificationResult {
        // Prefer the AI verdict when it is at least as confident; otherwise keep heuristic
        // but annotate. Never let AI silently upgrade KEEP->DELETE at low confidence.
        if (ai.action == RecommendedAction.DELETE && ai.confidence < 0.7) {
            return heuristic.copy(
                reason = "${heuristic.reason} (AI 倾向删除但置信不足)",
            )
        }
        return if (ai.confidence >= heuristic.confidence) ai else heuristic
    }

    private fun quarantineRootPath(settings: AiJanitorSettings): String? {
        val raw = settings.quarantineDir.trim()
        if (raw.isEmpty()) return null
        val f = java.io.File(raw)
        return if (f.isAbsolute) f.path else project.basePath?.let { "$it/$raw" }
    }

    companion object {
        private val FOLDED_DIR_CATEGORIES = setOf(
            FileCategory.AI_CONFIG, FileCategory.PROJECT_CONFIG, FileCategory.TEMP,
        )

        @JvmStatic
        fun getInstance(project: Project): FileJanitorService =
            project.getService(FileJanitorService::class.java)
    }
}
