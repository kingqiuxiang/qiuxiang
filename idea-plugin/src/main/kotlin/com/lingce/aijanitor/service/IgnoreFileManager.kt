package com.lingce.aijanitor.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.nio.charset.StandardCharsets

/**
 * Adds files/directories to VCS ignore (.gitignore) and/or marks them as
 * excluded in the IDE module model.
 */
class IgnoreFileManager(private val project: Project) {

    private val log = logger<IgnoreFileManager>()

    data class Outcome(val gitIgnored: Boolean, val excluded: Boolean, val detail: String)

    /**
     * @param addToGitIgnore append a matching entry to the nearest .gitignore
     * @param markExcluded   mark a directory as excluded in its module
     */
    fun ignore(file: VirtualFile, addToGitIgnore: Boolean, markExcluded: Boolean): Outcome {
        var git = false
        var excl = false
        val details = mutableListOf<String>()

        if (addToGitIgnore) {
            runCatching { addGitIgnoreEntry(file) }
                .onSuccess { if (it != null) { git = true; details += "已写入 ${it}" } }
                .onFailure { log.warn("gitignore update failed: ${it.message}") }
        }
        if (markExcluded && file.isDirectory) {
            runCatching { markAsExcluded(file) }
                .onSuccess { if (it) { excl = true; details += "已标记为 Excluded" } }
                .onFailure { log.warn("exclude failed: ${it.message}") }
        }
        return Outcome(git, excl, details.joinToString("; ").ifEmpty { "未做更改" })
    }

    private fun gitIgnoreLineFor(gitignore: VirtualFile, target: VirtualFile): String {
        val base = gitignore.parent
        val rel = VfsUtil.getRelativePath(target, base, '/') ?: target.name
        return if (target.isDirectory) "$rel/" else rel
    }

    /** @return the .gitignore path written to, or null if the entry already existed. */
    private fun addGitIgnoreEntry(target: VirtualFile): String? {
        val gitignore = findOrCreateGitIgnore(target) ?: return null
        val line = gitIgnoreLineFor(gitignore, target)
        var written: String? = null
        ApplicationManager.getApplication().runWriteAction {
            val existing = String(gitignore.contentsToByteArray(), StandardCharsets.UTF_8)
            val lines = existing.split('\n').map { it.trim() }
            if (lines.any { it == line || it == line.trimEnd('/') }) return@runWriteAction
            val sb = StringBuilder(existing)
            if (existing.isNotEmpty() && !existing.endsWith("\n")) sb.append('\n')
            if (!existing.contains("# Added by AI File Janitor")) {
                sb.append("\n# Added by AI File Janitor\n")
            }
            sb.append(line).append('\n')
            gitignore.setBinaryContent(sb.toString().toByteArray(StandardCharsets.UTF_8))
            written = "${gitignore.path} ($line)"
        }
        return written
    }

    private fun findOrCreateGitIgnore(target: VirtualFile): VirtualFile? {
        // Walk up from the target to find an existing .gitignore.
        var dir = if (target.isDirectory) target else target.parent
        val root = project.baseDir()
        while (dir != null) {
            dir.findChild(".gitignore")?.let { return it }
            if (root != null && dir == root) break
            dir = dir.parent
        }
        // None found: create one at the project base dir.
        val baseDir = root ?: target.parent ?: return null
        var created: VirtualFile? = null
        ApplicationManager.getApplication().runWriteAction {
            created = baseDir.findChild(".gitignore") ?: baseDir.createChildData(this, ".gitignore")
        }
        return created
    }

    private fun markAsExcluded(dir: VirtualFile): Boolean {
        val module = ProjectFileIndex.getInstance(project).getModuleForFile(dir) ?: return false
        ModuleRootModificationUtil.updateExcludedFolders(
            module,
            findContentRoot(module, dir) ?: return false,
            emptyList(),
            listOf(dir.url),
        )
        return true
    }

    private fun findContentRoot(
        module: com.intellij.openapi.module.Module,
        file: VirtualFile,
    ): VirtualFile? {
        val roots = com.intellij.openapi.roots.ModuleRootManager.getInstance(module).contentRoots
        return roots.firstOrNull { VfsUtil.isAncestor(it, file, false) }
    }

    private fun Project.baseDir(): VirtualFile? =
        basePath?.let { com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(it) }
}
