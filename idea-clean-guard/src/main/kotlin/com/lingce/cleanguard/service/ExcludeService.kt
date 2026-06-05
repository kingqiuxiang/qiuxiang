package com.lingce.cleanguard.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.lingce.cleanguard.model.ClassifiedFile
import java.io.File
import java.nio.charset.StandardCharsets

class ExcludeService(private val project: Project) {

    fun exclude(classified: ClassifiedFile): Boolean {
        val root = project.basePath ?: return false
        val target = File(root, classified.path)
        val gitignoreUpdated = appendGitignore(root, classified.path)
        val ideExcluded = excludeInIde(target)
        if (gitignoreUpdated || ideExcluded) {
            CleanGuardNotifier.info(project, "已加入 ignore/exclude: ${classified.path}")
        }
        return gitignoreUpdated || ideExcluded
    }

    private fun appendGitignore(projectRoot: String, relativePath: String): Boolean {
        val gitignore = File(projectRoot, ".gitignore")
        val entry = normalizeGitignoreEntry(relativePath)
        val existing = if (gitignore.exists()) gitignore.readText(StandardCharsets.UTF_8) else ""
        if (existing.lines().any { it.trim() == entry || it.trim() == "/$entry" || it.trim() == entry.trimStart('/') }) {
            return false
        }
        val marker = "# Clean Guard auto-exclude"
        val block = buildString {
            if (!existing.contains(marker)) {
                appendLine()
                appendLine(marker)
            }
            appendLine(entry)
        }
        gitignore.appendText(block, StandardCharsets.UTF_8)
        refreshVfs(gitignore)
        return true
    }

    private fun normalizeGitignoreEntry(path: String): String {
        val normalized = path.replace('\\', '/').trim('/')
        return if (normalized.contains('/')) normalized else "/$normalized"
    }

    private fun excludeInIde(target: File): Boolean {
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target) ?: return false
        var excluded = false
        for (module in ModuleManager.getInstance(project).modules) {
            ModuleRootModificationUtil.updateModel(module) { rootModel ->
                for (entry in rootModel.contentEntries) {
                    val entryRoot = entry.file ?: continue
                    if (VfsUtil.isAncestor(entryRoot, vf, false) || entryRoot == vf) {
                        val relative = VfsUtil.getRelativePath(vf, entryRoot)?.replace('\\', '/')
                            ?: vf.name
                        entry.addExcludeFolder(relative)
                        excluded = true
                    }
                }
            }
        }
        return excluded
    }

    private fun refreshVfs(file: File) {
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)?.refresh(false, false)
    }

    companion object {
        fun forProject(project: Project?): ExcludeService? =
            project?.let { ExcludeService(it) }
    }
}
