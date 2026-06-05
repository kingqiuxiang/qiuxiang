package com.lingce.cleankeeper.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object IgnoreManager {

    fun addToGitIgnore(project: Project, relativePath: String): Boolean {
        val base = project.basePath ?: return false
        val gitignore = File(base, ".gitignore")
        val entry = normalizeEntry(relativePath)

        if (!gitignore.exists()) {
            gitignore.writeText("# Added by AI File CleanKeeper\n$entry\n")
            refresh(project, gitignore)
            return true
        }

        val existing = gitignore.readText()
        if (existing.lines().any { it.trim() == entry || matchesIgnoreLine(it.trim(), entry) }) {
            return false
        }

        val updated = if (existing.endsWith("\n")) existing else "$existing\n"
        gitignore.writeText("$updated# Added by AI File CleanKeeper\n$entry\n")
        refresh(project, gitignore)
        return true
    }

    fun addToIdeExclude(project: Project, relativePath: String): Boolean {
        val file = resolveVirtualFile(project, relativePath) ?: return false
        return WriteAction.compute<Boolean, RuntimeException> {
            ProjectRootManager.getInstance(project).fileIndex.isExcluded(file) ||
                addToGitIgnore(project, relativePath)
        }
    }

    fun addToIgnore(project: Project, relativePath: String): Pair<Boolean, Boolean> {
        val git = addToGitIgnore(project, relativePath)
        val ide = addToIdeExclude(project, relativePath)
        return git to ide
    }

    private fun normalizeEntry(relativePath: String): String {
        val normalized = relativePath.replace('\\', '/').trimStart('/')
        return if (normalized.contains('/') || normalized.startsWith(".")) {
            normalized
        } else {
            "/$normalized"
        }
    }

    private fun matchesIgnoreLine(line: String, entry: String): Boolean {
        if (line.startsWith("#") || line.isBlank()) return false
        return line == entry || line == entry.removePrefix("/") || line.endsWith(entry)
    }

    private fun resolveVirtualFile(project: Project, relativePath: String): VirtualFile? {
        val base = project.basePath ?: return null
        return LocalFileSystem.getInstance().findFileByPath(File(base, relativePath).absolutePath)
    }

    private fun refresh(@Suppress("UNUSED_PARAMETER") project: Project, file: File) {
        ApplicationManager.getApplication().invokeLater {
            LocalFileSystem.getInstance().findFileByIoFile(file)?.let {
                it.refresh(false, false)
                VfsUtil.markDirtyAndRefresh(false, false, false, it)
            }
        }
    }
}

object QuarantineManager {

    fun quarantine(project: Project, file: VirtualFile): File? {
        val settings = com.lingce.cleankeeper.settings.CleanKeeperSettings.getInstance()
        val base = project.basePath ?: return null
        val relative = FileClassifierService.getInstance(project).getRelativePath(file)
        val targetDir = File(base, settings.quarantineDir)
        targetDir.mkdirs()

        val timestamp = System.currentTimeMillis()
        val safeName = relative.replace('/', '_').replace('\\', '_')
        val target = File(targetDir, "${timestamp}_$safeName")

        return WriteAction.compute<File?, RuntimeException> {
            try {
                val source = File(file.path)
                if (!source.exists()) return@compute null
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                LocalFileSystem.getInstance().refreshIoFiles(listOf(source, target))
                target
            } catch (_: Exception) {
                null
            }
        }
    }

    fun deleteFile(file: VirtualFile): Boolean {
        return WriteAction.compute<Boolean, RuntimeException> {
            try {
                file.delete(this)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
