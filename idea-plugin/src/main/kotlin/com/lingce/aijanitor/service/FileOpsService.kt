package com.lingce.aijanitor.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.lingce.aijanitor.settings.AiJanitorSettings
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Low-level, write-action wrapped file operations: delete and quarantine (move).
 */
class FileOpsService(private val project: Project) {

    private val log = logger<FileOpsService>()

    fun delete(file: VirtualFile): Result<Unit> = runCatching {
        ApplicationManager.getApplication().runWriteAction {
            file.delete(this)
        }
    }.onFailure { log.warn("delete failed for ${file.path}: ${it.message}") }

    /**
     * Move [file] into the configured quarantine directory, preserving its
     * relative path under a timestamped folder so nothing is overwritten.
     *
     * @return the destination path on success.
     */
    fun quarantine(file: VirtualFile): Result<String> = runCatching {
        val settings = AiJanitorSettings.getInstance()
        val quarantineRoot = resolveQuarantineRoot(settings.quarantineDir)
            ?: error("无法解析隔离目录: ${settings.quarantineDir}")

        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val relUnderProject = project.basePath?.let { base ->
            VfsUtil.getRelativePath(file, LocalFileSystem.getInstance().findFileByPath(base) ?: file, '/')
        } ?: file.name
        val safeRel = relUnderProject.ifBlank { file.name }

        var dest = File(quarantineRoot, "$stamp/$safeRel")
        if (dest.exists()) dest = File(quarantineRoot, "$stamp-${System.nanoTime()}/$safeRel")
        dest.parentFile.mkdirs()

        val targetDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dest.parentFile)
            ?: error("无法创建隔离目标目录")

        var resultPath = dest.absolutePath
        ApplicationManager.getApplication().runWriteAction {
            // If a same-named child already exists, rename it out of the way.
            targetDir.findChild(file.name)?.delete(this)
            file.move(this, targetDir)
        }
        resultPath
    }.onFailure { log.warn("quarantine failed for ${file.path}: ${it.message}") }

    private fun resolveQuarantineRoot(configured: String): File? {
        val raw = configured.trim().ifEmpty { ".ai-janitor/quarantine" }
        val file = File(raw)
        val root = if (file.isAbsolute) {
            file
        } else {
            val base = project.basePath ?: return null
            File(base, raw)
        }
        root.mkdirs()
        return if (root.isDirectory) root else null
    }
}
