package com.lingce.aijanitor.core

import com.google.gson.JsonParser
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.lingce.aijanitor.model.CleanupAction
import com.lingce.aijanitor.model.FileCategory
import com.lingce.aijanitor.model.ScanItem
import com.lingce.aijanitor.settings.AiJanitorSettings
import java.util.Locale

/**
 * "AI 深度清理" analyzer.
 *
 * Unlike the regular scan (which only looks at untracked files), deep clean
 * inspects **every** file — committed or not — and surfaces only the ones worth
 * removing:
 *  - temporary / junk files, and
 *  - "orphan" files whose base name is referenced nowhere else in the project
 *    (no reference relationship found).
 *
 * Files that should clearly be kept (entry points, configs, referenced files)
 * are never shown. Temp files default to delete; orphans default to "move into
 * the git-excluded folder" (reversible), and the user can switch either one.
 */
class DeepCleanAnalyzer(private val project: Project) {

    private val skippedDirs = setOf(
        ".git", ".hg", ".svn", ".idea", ".gradle", "node_modules", "build",
        "dist", "out", "target", ".next", ".nuxt", "venv", ".venv", "__pycache__",
        ".mvn", "vendor", ".cache", "coverage", ".pytest_cache", "bin", ".dart_tool",
    )

    /** File names that are always kept (entry points / project metadata). */
    private val keepNames = setOf(
        "readme", "readme.md", "readme.txt", "license", "license.md", "license.txt",
        "notice", "changelog.md", "contributing.md", "code_of_conduct.md", "security.md",
        "package.json", "package-lock.json", "yarn.lock", "pnpm-lock.yaml",
        "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle",
        "settings.gradle.kts", "gradle.properties", "makefile", "dockerfile",
        "go.mod", "go.sum", "cargo.toml", "cargo.lock", "requirements.txt",
        "pipfile", "pipfile.lock", "pyproject.toml", "composer.json", "gemfile",
    )

    /** Base names (without extension) that are typical entry points. */
    private val entryBaseNames = setOf(
        "index", "main", "app", "application", "lib", "mod", "__init__",
        "program", "startup", "bootstrap", "server", "client",
    )

    /** Very common base names that are almost always referenced — never treat as orphans. */
    private val genericBaseNames = setOf(
        "utils", "util", "types", "type", "const", "constants", "config",
        "helper", "helpers", "common", "base", "core", "model", "models",
        "style", "styles", "theme", "test", "tests", "spec", "api", "service",
    )

    fun analyze(indicator: ProgressIndicator?): ProjectScanner.ScanResult {
        val settings = AiJanitorSettings.getInstance()
        val collected = ReadAction.compute<Collected, RuntimeException> { collect(indicator) }

        var items = collected.items
        if (settings.aiConfigured() && items.isNotEmpty()) {
            indicator?.text = "AI 复核可清理文件…"
            items = aiRefine(items, collected.snippets, indicator)
        }
        return ProjectScanner.ScanResult(items, collected.snippets, collected.scannedCount)
    }

    private data class Collected(
        val items: List<ScanItem>,
        val snippets: Map<String, String>,
        val scannedCount: Int,
    )

    private fun collect(indicator: ProgressIndicator?): Collected {
        val settings = AiJanitorSettings.getInstance().state
        val extraPatterns = settings.extraTempPatterns.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val aiKeepPatterns = settings.aiKeepPatterns.split(',').map { it.trim() }.filter { it.isNotEmpty() }

        val base = project.guessProjectDir() ?: return Collected(emptyList(), emptyMap(), 0)
        val basePath = base.path
        val ignoreRoots = listOf(settings.archiveDir, settings.ignoreDir).map { it.replace('\\', '/').trim('/') }

        // Pass 1: walk + read content into a reference corpus.
        data class Entry(val file: VirtualFile, val rel: String, val snippet: String?)
        val entries = ArrayList<Entry>()
        val contents = HashMap<String, String>()
        val snippets = HashMap<String, String>()
        var totalIndexedChars = 0L
        val maxIndexChars = 40_000_000L

        val stack = ArrayDeque<VirtualFile>()
        base.children?.forEach { stack.addLast(it) }
        while (stack.isNotEmpty()) {
            indicator?.checkCanceled()
            val vf = stack.removeLast()
            if (!vf.isValid) continue
            val rel = relativize(basePath, vf.path)
            if (vf.isDirectory) {
                if (vf.name.lowercase(Locale.ROOT) in skippedDirs) continue
                if (ignoreRoots.any { it.isNotEmpty() && (rel == it || rel.startsWith("$it/")) }) continue
                vf.children?.forEach { stack.addLast(it) }
                continue
            }
            indicator?.text2 = rel
            val text = readText(vf)
            if (text != null) {
                snippets[rel] = text.take(4096)
                if (totalIndexedChars < maxIndexChars) {
                    val lower = text.lowercase(Locale.ROOT)
                    contents[rel] = lower
                    totalIndexedChars += lower.length
                }
            }
            entries.add(Entry(vf, rel, text?.take(4096)))
        }

        // Pass 2: decide which files are actionable.
        val items = ArrayList<ScanItem>()
        for (e in entries) {
            indicator?.checkCanceled()
            val classification = HeuristicClassifier.classify(e.file, e.rel, e.snippet, extraPatterns, aiKeepPatterns)

            // Temp / AI-junk → always actionable (delete).
            if (classification.category == FileCategory.TEMP || classification.category == FileCategory.AI_JUNK) {
                items.add(
                    ScanItem(
                        file = e.file,
                        relativePath = e.rel,
                        category = classification.category,
                        reason = classification.reason,
                        action = CleanupAction.DELETE,
                        selected = true,
                    )
                )
                continue
            }

            // Configs / AI-configs / explicit keep files are kept (hidden).
            if (classification.category == FileCategory.PROJECT_CONFIG ||
                classification.category == FileCategory.AI_CONFIG ||
                isKeepFile(e.file, e.rel)
            ) {
                continue
            }

            // Orphan detection: base name referenced nowhere else?
            if (isOrphan(e.rel, contents)) {
                items.add(
                    ScanItem(
                        file = e.file,
                        relativePath = e.rel,
                        category = FileCategory.SUSPICIOUS,
                        reason = "未发现被引用（孤立文件），建议删除或移入 .git/info/exclude",
                        action = CleanupAction.IGNORE,
                        selected = false,
                    )
                )
            }
        }
        return Collected(items, snippets, entries.size)
    }

    private fun isKeepFile(file: VirtualFile, rel: String): Boolean {
        val name = file.name.lowercase(Locale.ROOT)
        if (name in keepNames) return true
        if (name.startsWith(".")) return true // dotfiles are usually tooling config
        val baseName = baseNameOf(name)
        if (baseName in entryBaseNames) return true
        // Files directly at project root are typically meaningful (entry/metadata).
        if (!rel.contains('/')) {
            val ext = file.extension?.lowercase(Locale.ROOT).orEmpty()
            if (ext in setOf("md", "txt", "yml", "yaml", "toml", "json", "xml", "gradle", "kts", "properties")) return true
        }
        return false
    }

    private fun isOrphan(rel: String, contents: Map<String, String>): Boolean {
        val fileName = rel.substringAfterLast('/').lowercase(Locale.ROOT)
        val baseName = baseNameOf(fileName)
        if (baseName.length < 3) return false
        if (baseName in genericBaseNames) return false
        // Referenced if any OTHER file mentions the base name or the full file name.
        for ((path, content) in contents) {
            if (path == rel) continue
            if (content.contains(baseName) || content.contains(fileName)) return false
        }
        return true
    }

    private fun baseNameOf(fileName: String): String {
        val dot = fileName.indexOf('.')
        return if (dot > 0) fileName.substring(0, dot) else fileName
    }

    private fun readText(file: VirtualFile): String? = try {
        when {
            file.length == 0L || file.length > 1_000_000L -> null
            file.fileType.isBinary -> null
            else -> String(file.contentsToByteArray(), file.charset)
        }
    } catch (_: Exception) {
        null
    }

    private fun relativize(basePath: String, path: String): String {
        val normalizedBase = basePath.trimEnd('/')
        return if (path.startsWith("$normalizedBase/")) path.substring(normalizedBase.length + 1) else path
    }

    // ── AI review ─────────────────────────────────────────────────────────────

    private val systemPrompt = """
        你是一个严格的代码仓库深度清理助手。你会收到若干"疑似可清理"的文件（临时文件或在项目中找不到引用的孤立文件）。
        请判断每个文件应当如何处理，返回一个 JSON 数组，元素形如：
        {"index": <int>, "decision": "delete|ignore|keep", "reason": "<不超过30字的中文理由>"}
        - delete：确为无用的临时文件或废弃文件，可直接删除。
        - ignore：可能还有用但不应留在工作区，建议移动到 git 排除目录。
        - keep：实际有用（例如文档、入口、被动态引用），不应清理。
        判断要保守：拿不准时返回 keep。只返回 JSON 数组，不要任何额外文字。
    """.trimIndent()

    private fun aiRefine(
        items: List<ScanItem>,
        snippets: Map<String, String>,
        indicator: ProgressIndicator?,
    ): List<ScanItem> {
        val settings = AiJanitorSettings.getInstance()
        val client = AiClient(settings.state.baseUrl, settings.apiKey, settings.state.model)
        val batchSize = settings.state.aiBatchSize.coerceAtLeast(1)
        val kept = ArrayList<ScanItem>()

        items.chunked(batchSize).forEach { chunk ->
            indicator?.checkCanceled()
            try {
                val prompt = buildPrompt(chunk, snippets)
                val content = client.chat(systemPrompt, prompt)
                applyDecisions(content, chunk, kept)
            } catch (e: Exception) {
                LOG.warn("AI 深度复核失败，保留启发式结果: ${e.message}")
                kept.addAll(chunk)
            }
        }
        return kept
    }

    private fun buildPrompt(chunk: List<ScanItem>, snippets: Map<String, String>): String {
        val sb = StringBuilder("请判断以下 ${chunk.size} 个文件：\n\n")
        chunk.forEachIndexed { i, item ->
            val snippet = snippets[item.relativePath]?.take(600)?.replace("```", "ˋˋˋ") ?: "(二进制或空文件)"
            sb.append("### index=$i\n")
            sb.append("path: ${item.relativePath}\n")
            sb.append("heuristic: ${item.category.name.lowercase()} (${item.reason})\n")
            sb.append("content:\n```\n$snippet\n```\n\n")
        }
        return sb.toString()
    }

    private fun applyDecisions(content: String, chunk: List<ScanItem>, kept: MutableList<ScanItem>) {
        val array = extractArray(content)
        if (array == null) {
            kept.addAll(chunk)
            return
        }
        val decisions = HashMap<Int, Pair<String, String>>()
        for (el in array) {
            val obj = el.asJsonObject
            val idx = obj.get("index")?.asInt ?: continue
            val decision = obj.get("decision")?.asString?.lowercase()?.trim() ?: continue
            val reason = obj.get("reason")?.asString?.takeIf { it.isNotBlank() } ?: ""
            decisions[idx] = decision to reason
        }
        chunk.forEachIndexed { i, item ->
            val (decision, reason) = decisions[i] ?: ("keep" to "")
            when (decision) {
                "delete" -> {
                    item.action = CleanupAction.DELETE
                    item.selected = true
                    if (reason.isNotEmpty()) item.reason = "AI：$reason"
                    kept.add(item)
                }
                "ignore" -> {
                    item.action = CleanupAction.IGNORE
                    if (reason.isNotEmpty()) item.reason = "AI：$reason"
                    kept.add(item)
                }
                // "keep" or unknown → dropped (not shown)
            }
        }
    }

    private fun extractArray(content: String) = try {
        val start = content.indexOf('[')
        val end = content.lastIndexOf(']')
        if (start < 0 || end <= start) null
        else JsonParser.parseString(content.substring(start, end + 1)).asJsonArray
    } catch (_: Exception) {
        null
    }

    companion object {
        private val LOG = logger<DeepCleanAnalyzer>()
    }
}
