package com.aifileguard.classify

import com.aifileguard.model.FileCategory
import com.aifileguard.model.FileVerdict
import com.aifileguard.settings.AiGuardSettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.streams.asSequence

/**
 * Project-scoped service that scans the project tree and classifies files.
 */
@Service(Service.Level.PROJECT)
class FileGuardScanner(private val project: Project) {

    /**
     * Scan the whole project.
     *
     * @param allowAi when false, only the fast offline rules run (used for the
     *   automatic scan on project open to avoid latency / API cost).
     */
    fun scan(indicator: ProgressIndicator?, allowAi: Boolean = true): List<FileVerdict> {
        val root = project.guessProjectDir()?.toNioPathOrNull() ?: return emptyList()
        val settings = AiGuardSettings.getInstance().state
        val ruleClassifier = RuleClassifier(settings)
        val aiClassifier = AiClassifier(settings)

        val results = ArrayList<FileVerdict>()
        val files = Files.walk(root).use { stream ->
            stream.asSequence()
                .filter { Files.isRegularFile(it) }
                .filter { !isInSkippedDir(root, it) }
                .toList()
        }

        val total = files.size
        files.forEachIndexed { index, path ->
            indicator?.checkCanceled()
            indicator?.fraction = if (total == 0) 1.0 else index.toDouble() / total
            indicator?.text2 = root.relativize(path).toString()

            val verdict = classifyPath(root, path, settings, ruleClassifier, aiClassifier, allowAi)
            if (verdict.category != FileCategory.NORMAL) {
                results.add(verdict)
            }
        }

        return results.sortedWith(
            compareByDescending<FileVerdict> { severity(it.category) }
                .thenByDescending { it.confidence }
        )
    }

    /**
     * Classify a single file (used by the new-file watcher). Rules only unless [withAi].
     */
    fun classifySingle(path: Path, withAi: Boolean): FileVerdict? {
        val root = project.guessProjectDir()?.toNioPathOrNull() ?: return null
        if (!Files.isRegularFile(path) || isInSkippedDir(root, path)) return null
        val settings = AiGuardSettings.getInstance().state
        val ruleClassifier = RuleClassifier(settings)
        val aiClassifier = AiClassifier(settings)
        val verdict = classifyPath(root, path, settings, ruleClassifier, aiClassifier, allowAi = withAi)
        return if (verdict.category == FileCategory.NORMAL) null else verdict
    }

    private fun classifyPath(
        root: Path,
        path: Path,
        settings: AiGuardSettings.State,
        ruleClassifier: RuleClassifier,
        aiClassifier: AiClassifier,
        allowAi: Boolean = true,
    ): FileVerdict {
        val relative = root.relativize(path).toString().replace('\\', '/')
        val size = runCatching { Files.size(path) }.getOrDefault(0L)
        var verdict = ruleClassifier.classify(relative, path.name, size, path.toAbsolutePath().toString())

        val needsAi = verdict.category == FileCategory.SUSPICIOUS ||
            (verdict.category == FileCategory.NORMAL && verdict.confidence < 0.3)
        if (allowAi && needsAi && aiClassifier.isConfigured() && size <= settings.maxAiFileSizeKb * 1024L) {
            val snippet = readSnippet(path, settings.maxAiFileSizeKb * 1024)
            if (snippet != null) {
                verdict = aiClassifier.classify(verdict, snippet)
            }
        }
        return verdict
    }

    private fun readSnippet(path: Path, maxBytes: Int): String? = runCatching {
        val bytes = Files.readAllBytes(path)
        if (looksBinary(bytes)) return@runCatching "<binary file, ${bytes.size} bytes>"
        val text = String(bytes, Charsets.UTF_8)
        if (text.length > MAX_SNIPPET_CHARS) text.substring(0, MAX_SNIPPET_CHARS) else text
    }.getOrNull()

    private fun looksBinary(bytes: ByteArray): Boolean {
        val sample = bytes.take(2048)
        if (sample.isEmpty()) return false
        val nonText = sample.count { b -> b.toInt() == 0 }
        return nonText > 0
    }

    private fun isInSkippedDir(root: Path, path: Path): Boolean {
        val rel = root.relativize(path)
        for (segment in rel) {
            if (segment.toString() in SKIPPED_DIRS) return true
        }
        return false
    }

    companion object {
        private const val MAX_SNIPPET_CHARS = 6000

        private val SKIPPED_DIRS = setOf(
            ".git", ".hg", ".svn",
            "node_modules", ".gradle", ".m2",
            "build", "out", "dist", "target",
            ".idea", "venv", ".venv", "__pycache__",
            ".next", ".nuxt", "vendor", "Pods",
        )

        private fun severity(category: FileCategory): Int = when (category) {
            FileCategory.SUSPICIOUS -> 5
            FileCategory.AI_GENERATED_USELESS -> 4
            FileCategory.TEMPORARY -> 3
            FileCategory.AI_CONFIG -> 2
            FileCategory.PROJECT_CONFIG -> 1
            FileCategory.NORMAL -> 0
        }
    }
}

private fun com.intellij.openapi.vfs.VirtualFile.toNioPathOrNull(): Path? =
    runCatching { this.toNioPath() }.getOrNull()
