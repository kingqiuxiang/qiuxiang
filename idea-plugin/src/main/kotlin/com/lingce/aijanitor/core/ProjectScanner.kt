package com.lingce.aijanitor.core

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.lingce.aijanitor.model.ScanItem
import com.lingce.aijanitor.settings.AiJanitorSettings
import java.nio.file.Files
import java.nio.file.Path

/** Walks a project tree and produces an initial heuristic classification. */
class ProjectScanner(private val project: Project) {

    /** Directories skipped during scanning — keep only truly essential ones (VCS, massive deps, build output). */
    private val skippedDirs = setOf(
        ".git", ".hg", ".svn",       // VCS internals — never touch
        "build", "dist", "out", "target", // build artifacts
        "venv", ".venv",              // Python virtual environments
    )

    data class ScanResult(val items: List<ScanItem>, val snippets: Map<String, String>, val scannedCount: Int)

    fun scan(indicator: ProgressIndicator?): ScanResult = ReadAction.compute<ScanResult, RuntimeException> {
        val settings = AiJanitorSettings.getInstance().state
        val extraPatterns = settings.extraTempPatterns.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val aiKeepPatterns = settings.aiKeepPatterns.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val permanentIgnorePatterns = settings.permanentIgnorePatterns.split(',').map { it.trim() }.filter { it.isNotEmpty() }

        val base = project.guessProjectDir() ?: return@compute ScanResult(emptyList(), emptyMap(), 0)
        val basePath = base.path
        val ignoreRoots = listOf(settings.archiveDir, settings.ignoreDir).map { it.replace('\\', '/').trim('/') }

        // Read .git/info/exclude + .gitignore patterns once before walking.
        val gitIgnorePatterns = readGitInfoExcludePatterns(Path.of(base.path)) +
            readGitignorePatterns(Path.of(base.path))

        // Read git-tracked files (files already under version control are never cleanup targets).
        val gitTrackedFiles = readGitTrackedFiles(Path.of(base.path))

        val items = ArrayList<ScanItem>()
        val snippets = HashMap<String, String>()
        var scanned = 0
        var gitTrackedSkipped = 0

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
                // Skip directories already excluded by git ignore rules — avoids
                // walking every file inside an excluded tree.
                if (isGitExcluded(rel, gitIgnorePatterns, isDir = true)) continue
                vf.children?.forEach { stack.addLast(it) }
                continue
            }

            scanned++
            indicator?.text2 = rel

            // Skip files already tracked by git — they are legitimate project files.
            if (rel in gitTrackedFiles) {
                gitTrackedSkipped++
                continue
            }

            // Skip files matching permanent-ignore globs (user-chosen "以后均忽略").
            if (permanentIgnorePatterns.any { matchesFileNameGlob(vf.name, it) }) {
                continue
            }

            // Skip files already covered by git ignore rules (.gitignore / .git/info/exclude).
            if (isGitExcluded(rel, gitIgnorePatterns, isDir = false)) {
                continue
            }

            val snippet = readSnippet(vf)
            if (snippet != null) snippets[rel] = snippet
            val classification = HeuristicClassifier.classify(vf, rel, snippet, extraPatterns, aiKeepPatterns)
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

    // ── Git tracked files ───────────────────────────────────────────────────

    /** Returns the set of relative paths for all files tracked by git. */
    private fun readGitTrackedFiles(base: Path): Set<String> {
        if (resolveGitDir(base) == null) {
            LOG.info("No .git directory found at $base, skipping git-tracked check")
            return emptySet()
        }
        return runCatching {
            val process = ProcessBuilder("git", "ls-files", "--cached")
                .directory(base.toFile())
                .redirectErrorStream(true)
                .start()
            val result = process.inputStream.bufferedReader().use { reader ->
                reader.lineSequence()
                    .map { it.trim().replace('\\', '/') }
                    .filter { it.isNotEmpty() }
                    .toSet()
            }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                LOG.warn("git ls-files exited with code $exitCode")
            }
            LOG.info("Found ${result.size} git-tracked files")
            result
        }.onFailure { e ->
            LOG.warn("Failed to read git-tracked files: ${e.message}", e)
        }.getOrDefault(emptySet())
    }

    // ── .git/info/exclude helpers ───────────────────────────────────────────

    /** Parsed pattern from .git/info/exclude. */
    private data class ExcludePattern(
        val raw: String,
        val negated: Boolean,
        val dirOnly: Boolean,
        val regex: Regex,
    )

    /** Read non-comment, non-blank patterns from .git/info/exclude. */
    private fun readGitInfoExcludePatterns(base: Path): List<ExcludePattern> {
        val dotGit = resolveGitDir(base) ?: return emptyList()
        val excludeFile = dotGit.resolve("info").resolve("exclude")
        if (!Files.isRegularFile(excludeFile)) return emptyList()
        val lines = runCatching { Files.readString(excludeFile) }.getOrNull() ?: return emptyList()
        return lines.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { toExcludePattern(it) }
            .toList()
    }

    /** Read non-comment, non-blank patterns from the root .gitignore file. */
    private fun readGitignorePatterns(base: Path): List<ExcludePattern> {
        val gitignore = base.resolve(".gitignore")
        if (!Files.isRegularFile(gitignore)) return emptyList()
        val lines = runCatching { Files.readString(gitignore) }.getOrNull() ?: return emptyList()
        return lines.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { toExcludePattern(it) }
            .toList()
    }

    private fun resolveGitDir(base: Path): Path? {
        val dotGit = base.resolve(".git")
        return when {
            Files.isDirectory(dotGit) -> dotGit
            Files.isRegularFile(dotGit) -> {
                val text = runCatching { Files.readString(dotGit).trim() }.getOrNull() ?: return null
                val pointer = text.lineSequence()
                    .firstOrNull { it.startsWith("gitdir:") }
                    ?.substringAfter("gitdir:")?.trim()
                    ?: return null
                base.resolve(pointer).normalize()
            }
            else -> null
        }
    }

    /** Convert a raw gitignore line into an [ExcludePattern]. */
    private fun toExcludePattern(raw: String): ExcludePattern? {
        var pat = raw
        val negated = pat.startsWith("!")
        if (negated) pat = pat.substring(1)
        val dirOnly = pat.endsWith("/")
        if (dirOnly) pat = pat.dropLast(1)
        if (pat.isEmpty()) return null

        // Leading "/" anchors to project root
        val anchored = pat.startsWith("/")
        if (anchored) pat = pat.substring(1)

        val regexStr = gitignoreGlobToRegex(pat)
        if (regexStr.isBlank()) return null

        // Build final regex: unanchored patterns can match at any depth
        val finalRegex = if (anchored) {
            Regex("^$regexStr$")
        } else {
            Regex("(^|.*/)$regexStr$")
        }

        return ExcludePattern(raw = raw, negated = negated, dirOnly = dirOnly, regex = finalRegex)
    }

    /** Convert a gitignore-style glob (without leading/trailing /) to a regex fragment. */
    private fun gitignoreGlobToRegex(glob: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < glob.length) {
            when (val c = glob[i]) {
                '*' -> {
                    when {
                        // **/ at start or after /
                        i + 2 < glob.length && glob[i + 1] == '*' && glob[i + 2] == '/' -> {
                            sb.append("(.*/)?")
                            i += 3
                        }
                        // ** at end
                        i + 1 < glob.length && glob[i + 1] == '*' && i + 2 == glob.length -> {
                            sb.append(".*")
                            i += 2
                        }
                        // ** in middle
                        i + 1 < glob.length && glob[i + 1] == '*' -> {
                            sb.append(".*")
                            i += 2
                        }
                        else -> {
                            sb.append("[^/]*")
                            i++
                        }
                    }
                }
                '?' -> { sb.append("[^/]"); i++ }
                '.' -> { sb.append("\\."); i++ }
                else -> { sb.append(Regex.escape(c.toString())); i++ }
            }
        }
        return sb.toString()
    }

    /** Check if a path (file or directory) matches any of the git exclude patterns. */
    private fun isGitExcluded(relativePath: String, patterns: List<ExcludePattern>, isDir: Boolean): Boolean {
        val normalized = relativePath.replace('\\', '/')
        var excluded = false
        for (p in patterns) {
            // Direct regex match
            if (p.regex.matches(normalized)) {
                excluded = !p.negated
            }
            // For files, also check whether any parent directory is covered by a
            // dir-only pattern (e.g. /dirname/ matches dirname/foo.txt indirectly).
            if (!isDir && p.dirOnly) {
                val parent = normalized.substringBeforeLast('/', "")
                if (parent.isNotEmpty() && p.regex.matches(parent)) {
                    excluded = !p.negated
                }
            }
        }
        return excluded
    }

    private fun matchesFileNameGlob(name: String, glob: String): Boolean {
        if (glob.isBlank()) return false
        val regex = Regex(
            "^" + Regex.escape(glob.lowercase())
                .replace("\\*", ".*")
                .replace("\\?", ".") + "$"
        )
        return regex.matches(name.lowercase())
    }

    companion object {
        private val LOG = logger<ProjectScanner>()
    }
}
