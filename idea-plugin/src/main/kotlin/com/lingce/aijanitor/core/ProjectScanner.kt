package com.lingce.aijanitor.core

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.lingce.aijanitor.model.ScanItem
import com.lingce.aijanitor.settings.AiJanitorSettings

/** Walks a project tree and produces an initial heuristic classification. */
class ProjectScanner(private val project: Project) {

    private val skippedDirs = setOf(
        ".git", ".hg", ".svn", ".idea", ".gradle", "node_modules", "build",
        "dist", "out", "target", ".next", ".nuxt", "venv", ".venv", "__pycache__",
        ".mvn", "vendor", ".cache", "coverage", ".pytest_cache", "bin",
    )

    data class ScanResult(val items: List<ScanItem>, val snippets: Map<String, String>, val scannedCount: Int)

    fun scan(indicator: ProgressIndicator?): ScanResult = ReadAction.compute<ScanResult, RuntimeException> {
        val settings = AiJanitorSettings.getInstance().state
        val extraPatterns = settings.extraTempPatterns.split(',').map { it.trim() }.filter { it.isNotEmpty() }

        val base = project.guessProjectDir() ?: return@compute ScanResult(emptyList(), emptyMap(), 0)
        val basePath = base.path
        val ignoreRoots = listOf(settings.archiveDir, settings.ignoreDir).map { it.replace('\\', '/').trim('/') }

        val items = ArrayList<ScanItem>()
        val snippets = HashMap<String, String>()
        var scanned = 0

        val stack = ArrayDeque<VirtualFile>()
        base.children?.forEach { stack.addLast(it) }

        while (stack.isNotEmpty()) {
            indicator?.checkCanceled()
            val vf = stack.removeLast()
            if (!vf.isValid) continue
            val rel = relativize(basePath, vf.path)
            if (vf.isDirectory) {
                val nameLower = vf.name.lowercase()
                if (nameLower in skippedDirs) continue
                if (ignoreRoots.any { it.isNotEmpty() && (rel == it || rel.startsWith("$it/")) }) continue
                vf.children?.forEach { stack.addLast(it) }
                continue
            }

            scanned++
            indicator?.text2 = rel
            val snippet = readSnippet(vf)
            if (snippet != null) snippets[rel] = snippet
            val classification = HeuristicClassifier.classify(vf, rel, snippet, extraPatterns)
            items.add(
                ScanItem(
                    file = vf,
                    relativePath = rel,
                    category = classification.category,
                    reason = classification.reason,
                    action = classification.action,
                    selected = classification.selectedByDefault,
                )
            )
        }
        ScanResult(items, snippets, scanned)
    }

    private fun readSnippet(file: VirtualFile): String? {
        return try {
            if (file.length == 0L || file.length > 1_000_000L) return null
            if (file.fileType.isBinary) return null
            val bytes = file.contentsToByteArray()
            val limit = minOf(bytes.size, 4096)
            String(bytes, 0, limit, file.charset)
        } catch (e: Exception) {
            null
        }
    }

    private fun relativize(basePath: String, path: String): String {
        val normalizedBase = basePath.trimEnd('/')
        return if (path.startsWith("$normalizedBase/")) {
            path.substring(normalizedBase.length + 1)
        } else {
            path
        }
    }
}
