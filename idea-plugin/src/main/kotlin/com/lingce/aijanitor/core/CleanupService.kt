package com.lingce.aijanitor.core

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.lingce.aijanitor.model.CleanupAction
import com.lingce.aijanitor.model.ScanItem
import com.lingce.aijanitor.settings.AiJanitorSettings
import java.nio.file.Files
import java.nio.file.Path

/** Executes the chosen [CleanupAction] for a set of files. */
class CleanupService(private val project: Project) {

    data class Report(val deleted: Int, val archived: Int, val gitExcluded: Int, val errors: List<String>) {
        fun summary(): String {
            val parts = buildList {
                if (deleted > 0) add("删除 $deleted")
                if (archived > 0) add("转存 $archived")
                if (gitExcluded > 0) add("移入 .git/info/exclude $gitExcluded")
            }
            val head = if (parts.isEmpty()) "未执行任何操作" else parts.joinToString("，")
            return if (errors.isEmpty()) "$head。" else "$head；${errors.size} 个失败。"
        }
    }

    /** Applies actions on the EDT inside a single undoable command. */
    fun apply(items: List<ScanItem>): Report {
        var deleted = 0
        var archived = 0
        var gitExcluded = 0
        val errors = ArrayList<String>()

        val settings = AiJanitorSettings.getInstance().state
        val base = project.guessProjectDir()
            ?: return Report(0, 0, 0, listOf("找不到项目根目录"))

        // Collect files to add to .git/info/exclude (applied after the write action).
        val gitExcludePaths = ArrayList<String>()

        WriteCommandAction.runWriteCommandAction(project, "AI 文件清理", null, {
            val archiveDir by lazy { VfsUtil.createDirectoryIfMissing(base, settings.archiveDir) }

            for (item in items) {
                val file = item.file
                if (!file.isValid) {
                    errors.add("${item.relativePath}: 文件已失效")
                    continue
                }
                try {
                    when (item.action) {
                        CleanupAction.DELETE -> {
                            file.delete(this)
                            deleted++
                        }
                        CleanupAction.ARCHIVE -> {
                            val target = archiveDir ?: error("无法创建转存目录")
                            moveInto(file, target)
                            archived++
                        }
                        CleanupAction.IGNORE -> {
                            // Only add if not already covered by an existing exclude pattern
                            if (!isAlreadyExcluded(item.relativePath, Path.of(base.path))) {
                                gitExcludePaths.add(item.relativePath)
                            }
                            gitExcluded++
                        }
                        CleanupAction.KEEP -> {}
                    }
                } catch (e: Exception) {
                    LOG.warn("处理失败: ${item.relativePath}", e)
                    errors.add("${item.relativePath}: ${e.message}")
                }
            }

            // Write the git exclude entries for IGNORE actions.
            if (gitExcludePaths.isNotEmpty()) {
                runCatching {
                    val basePath = Path.of(base.path)
                    val excludeFile = resolveGitInfoExclude(basePath)
                    // Consolidate individual paths into folder patterns where possible.
                    val consolidated = consolidateToFolderPatterns(gitExcludePaths)
                    if (excludeFile != null) {
                        appendMissingLines(excludeFile, consolidated)
                    } else {
                        // Fallback: add to .gitignore
                        ensureGitIgnored(base, consolidated)
                    }
                }.onFailure { e ->
                    LOG.warn("写入 git exclude 失败", e)
                    errors.add("写入 .git/info/exclude 失败: ${e.message}")
                }
            }

            // Make archive folder git-ignored and IDE-excluded.
            if (archived > 0) {
                runCatching { excludeFromGit(base, listOf(settings.archiveDir)) }
                archiveDir?.let { runCatching { markExcluded(it) } }
            }

            // Also re-exclude the old ignoreDir if it exists on disk (for backward compat).
            val ignoreDirPath = settings.ignoreDir
            if (ignoreDirPath.isNotBlank()) {
                val ignoreDir = base.findChild(ignoreDirPath)
                if (ignoreDir != null) {
                    runCatching { excludeFromGit(base, listOf(ignoreDirPath)) }
                    runCatching { markExcluded(ignoreDir) }
                }
            }
        })

        return Report(deleted, archived, gitExcluded, errors)
    }

    private fun moveInto(file: VirtualFile, targetDir: VirtualFile) {
        if (VfsUtilCore.isAncestor(file, targetDir, false)) {
            error("不能移动到自身子目录")
        }
        if (file.parent == targetDir) return
        if (targetDir.findChild(file.name) != null) {
            val name = file.name
            val dot = name.lastIndexOf('.')
            val stamp = System.currentTimeMillis()
            val unique = if (dot > 0) "${name.substring(0, dot)}_$stamp${name.substring(dot)}" else "${name}_$stamp"
            file.rename(this, unique)
        }
        file.move(this, targetDir)
    }

    /**
     * Excludes helper folder patterns from Git using `.git/info/exclude`.
     * Falls back to `.gitignore` when the project is not a Git repository.
     */
    private fun excludeFromGit(base: VirtualFile, dirs: List<String>) {
        val entries = dirs.map { it.replace('\\', '/').trim('/') + "/" }.filter { it.length > 1 }.distinct()
        if (entries.isEmpty()) return
        val excludeFile = resolveGitInfoExclude(Path.of(base.path))
        if (excludeFile != null) {
            appendMissingLines(excludeFile, entries)
        } else {
            ensureGitIgnored(base, entries)
        }
    }

    /**
     * Checks whether [relativePath] is already covered by an existing pattern
     * in .git/info/exclude (including directory patterns that cover parent dirs).
     */
    private fun isAlreadyExcluded(relativePath: String, base: Path): Boolean {
        val normalized = relativePath.replace('\\', '/')
        val patterns = readExistingExcludePatterns(base)
        var excluded = false
        for (p in patterns) {
            if (p.regex.matches(normalized)) {
                excluded = !p.negated
            }
            // Also check if a dir-only pattern covers a parent directory
            if (p.dirOnly) {
                val parent = normalized.substringBeforeLast('/', "")
                if (parent.isNotEmpty() && p.regex.matches(parent)) {
                    excluded = !p.negated
                }
            }
        }
        return excluded
    }

    /** Simple pattern holder for existing exclude entries. */
    private data class ExistingPattern(val raw: String, val negated: Boolean, val dirOnly: Boolean, val regex: Regex)

    private fun readExistingExcludePatterns(base: Path): List<ExistingPattern> {
        val excludeFile = resolveGitInfoExclude(base) ?: return emptyList()
        if (!Files.isRegularFile(excludeFile)) return emptyList()
        val lines = runCatching { Files.readString(excludeFile) }.getOrNull() ?: return emptyList()
        return lines.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { parseExcludePattern(it) }
            .toList()
    }

    private fun parseExcludePattern(raw: String): ExistingPattern? {
        var pat = raw
        val negated = pat.startsWith("!")
        if (negated) pat = pat.substring(1)
        val dirOnly = pat.endsWith("/")
        if (dirOnly) pat = pat.dropLast(1)
        if (pat.isEmpty()) return null
        val anchored = pat.startsWith("/")
        if (anchored) pat = pat.substring(1)
        val regexStr = gitignoreGlobToRegex(pat)
        if (regexStr.isBlank()) return null
        val finalRegex = if (anchored) Regex("^$regexStr$") else Regex("(^|.*/)$regexStr$")
        return ExistingPattern(raw, negated, dirOnly, finalRegex)
    }

    private fun gitignoreGlobToRegex(glob: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < glob.length) {
            when (val c = glob[i]) {
                '*' -> {
                    when {
                        i + 2 < glob.length && glob[i + 1] == '*' && glob[i + 2] == '/' -> { sb.append("(.*/)?"); i += 3 }
                        i + 1 < glob.length && glob[i + 1] == '*' && i + 2 == glob.length -> { sb.append(".*"); i += 2 }
                        i + 1 < glob.length && glob[i + 1] == '*' -> { sb.append(".*"); i += 2 }
                        else -> { sb.append("[^/]*"); i++ }
                    }
                }
                '?' -> { sb.append("[^/]"); i++ }
                '.' -> { sb.append("\\."); i++ }
                else -> { sb.append(Regex.escape(c.toString())); i++ }
            }
        }
        return sb.toString()
    }

    /** Resolves `<gitdir>/info/exclude`, handling normal repos and worktrees/submodules (.git file). */
    private fun resolveGitInfoExclude(base: Path): Path? {
        val dotGit = base.resolve(".git")
        val gitDir: Path = when {
            Files.isDirectory(dotGit) -> dotGit
            Files.isRegularFile(dotGit) -> {
                val text = runCatching { Files.readString(dotGit).trim() }.getOrNull() ?: return null
                val pointer = text.lineSequence().firstOrNull { it.startsWith("gitdir:") }?.substringAfter("gitdir:")?.trim()
                    ?: return null
                base.resolve(pointer).normalize()
            }
            else -> return null
        }
        val info = gitDir.resolve("info")
        Files.createDirectories(info)
        return info.resolve("exclude")
    }

    private fun appendMissingLines(file: Path, entries: List<String>) {
        val existing = if (Files.exists(file)) Files.readString(file) else ""
        val present = existing.lines().map { it.trim() }.toSet()
        val toAdd = entries.filter { it !in present }
        if (toAdd.isEmpty()) return
        val sb = StringBuilder(existing)
        if (existing.isNotEmpty() && !existing.endsWith("\n")) sb.append('\n')
        if (!existing.contains(MARKER)) sb.append("\n$MARKER\n")
        toAdd.forEach { sb.append(it).append('\n') }
        Files.writeString(file, sb.toString())
    }

    /** Fallback used only when there is no Git repository. */
    private fun ensureGitIgnored(base: VirtualFile, entries: List<String>) {
        val gitignore = base.findChild(".gitignore") ?: base.createChildData(this, ".gitignore")
        val existing = VfsUtilCore.loadText(gitignore)
        val present = existing.lines().map { it.trim() }.toSet()
        val toAdd = entries.filter { it !in present }
        if (toAdd.isEmpty()) return
        val sb = StringBuilder(existing)
        if (existing.isNotEmpty() && !existing.endsWith("\n")) sb.append('\n')
        if (!existing.contains(MARKER)) sb.append("\n$MARKER\n")
        toAdd.forEach { sb.append(it).append('\n') }
        VfsUtil.saveText(gitignore, sb.toString())
    }

    /**
     * Consolidates individual file paths into folder-level patterns when a directory
     * has 3 or more files being excluded. Uses gitignore-style format:
     * - `/dirname/` for top-level directories (anchored, dir-only match)
     * - Falls back to individual paths for files in directories below threshold.
     */
    private fun consolidateToFolderPatterns(paths: List<String>): List<String> {
        val normalized = paths.map { it.replace('\\', '/').trimStart('/') }
        // Group by top-level directory
        val grouped = LinkedHashMap<String, MutableList<String>>()
        val rootFiles = ArrayList<String>()

        for (p in normalized) {
            val slashIdx = p.indexOf('/')
            if (slashIdx < 0) {
                rootFiles.add(p)
            } else {
                val dir = p.substring(0, slashIdx)
                grouped.getOrPut(dir) { ArrayList() }.add(p)
            }
        }

        val result = ArrayList<String>()
        // For directories with ≥3 files, use a single folder pattern
        for ((dir, files) in grouped) {
            if (files.size >= 3) {
                result.add("/$dir/")
            } else {
                files.forEach { result.add(it) }
            }
        }
        // Root files are added individually
        result.addAll(rootFiles)
        return result
    }

    private fun markExcluded(dir: VirtualFile) {
        val module = ModuleUtilCore.findModuleForFile(dir, project) ?: return
        val contentRoot = ProjectFileIndex.getInstance(project).getContentRootForFile(dir) ?: return
        ModuleRootModificationUtil.updateExcludedFolders(module, contentRoot, emptyList(), listOf(dir.url))
    }

    companion object {
        private val LOG = logger<CleanupService>()
        private const val MARKER = "# Added by AI File Janitor"
    }
}
