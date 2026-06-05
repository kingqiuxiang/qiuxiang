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

    data class Report(val deleted: Int, val archived: Int, val ignored: Int, val errors: List<String>) {
        fun summary(): String {
            val parts = buildList {
                if (deleted > 0) add("删除 $deleted")
                if (archived > 0) add("转存 $archived")
                if (ignored > 0) add("移入 ignore $ignored")
            }
            val head = if (parts.isEmpty()) "未执行任何操作" else parts.joinToString("，")
            return if (errors.isEmpty()) "$head。" else "$head；${errors.size} 个失败。"
        }
    }

    /** Applies actions on the EDT inside a single undoable command. */
    fun apply(items: List<ScanItem>): Report {
        var deleted = 0
        var archived = 0
        var ignored = 0
        val errors = ArrayList<String>()

        val settings = AiJanitorSettings.getInstance().state
        val base = project.guessProjectDir()
            ?: return Report(0, 0, 0, listOf("找不到项目根目录"))

        WriteCommandAction.runWriteCommandAction(project, "AI 文件清理", null, {
            val archiveDir by lazy { VfsUtil.createDirectoryIfMissing(base, settings.archiveDir) }
            val ignoreDir by lazy { VfsUtil.createDirectoryIfMissing(base, settings.ignoreDir) }

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
                            val target = ignoreDir ?: error("无法创建 ignore 目录")
                            moveInto(file, target)
                            ignored++
                        }
                        CleanupAction.KEEP -> {}
                    }
                } catch (e: Exception) {
                    LOG.warn("处理失败: ${item.relativePath}", e)
                    errors.add("${item.relativePath}: ${e.message}")
                }
            }

            // Make the helper folders git-ignored (locally) and IDE-excluded so they stay invisible.
            if (archived > 0 || ignored > 0) {
                runCatching { excludeFromGit(base, listOf(settings.archiveDir, settings.ignoreDir)) }
                if (archived > 0) archiveDir?.let { runCatching { markExcluded(it) } }
                if (ignored > 0) ignoreDir?.let { runCatching { markExcluded(it) } }
            }
        })

        return Report(deleted, archived, ignored, errors)
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
     * Excludes the helper folders from Git using the repo-local, **untracked**
     * `.git/info/exclude` file so the tracked `.gitignore` is never touched.
     * Falls back to `.gitignore` only when the project is not a Git repository.
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
