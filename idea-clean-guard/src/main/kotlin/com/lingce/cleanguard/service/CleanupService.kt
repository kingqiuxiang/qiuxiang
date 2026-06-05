package com.lingce.cleanguard.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.lingce.cleanguard.model.ClassifiedFile
import com.lingce.cleanguard.settings.CleanGuardSettings
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class CleanupService(private val project: Project) {

    private val quarantineRoot: File
        get() {
            val settings = CleanGuardSettings.getInstance().state
            return File(project.basePath ?: ".", settings.quarantineDir)
        }

    fun delete(classified: ClassifiedFile): Boolean {
        val vf = findVirtualFile(classified.path) ?: return false
        return ApplicationManager.getApplication().runWriteAction<Boolean> {
            try {
                vf.delete(this)
                CleanGuardNotifier.info(project, "已删除: ${classified.path}")
                true
            } catch (e: Exception) {
                CleanGuardNotifier.warn(project, "删除失败: ${classified.path} (${e.message})")
                false
            }
        }
    }

    fun quarantine(classified: ClassifiedFile): Boolean {
        val source = findVirtualFile(classified.path) ?: return false
        val targetDir = quarantineRoot.apply { mkdirs() }
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val safeName = classified.path.replace('/', '_').replace('\\', '_')
        val targetFileName = "${timestamp}_$safeName"
        val targetParent = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetDir)
            ?: return false

        return ApplicationManager.getApplication().runWriteAction<Boolean> {
            try {
                if (source.isDirectory) {
                    val destDir = targetParent.createChildDirectory(this, targetFileName)
                    VfsUtil.copyDirectory(this, source, destDir, null)
                    source.delete(this)
                } else {
                    val copied = VfsUtil.copy(this, source, targetParent)
                    copied.rename(this, targetFileName)
                    source.delete(this)
                }
                CleanGuardNotifier.info(project, "已转存到隔离区: ${classified.path}")
                true
            } catch (e: Exception) {
                CleanGuardNotifier.warn(project, "转存失败: ${classified.path} (${e.message})")
                false
            }
        }
    }

    fun applyAutoAction(classified: ClassifiedFile): Boolean {
        val settings = CleanGuardSettings.getInstance().state
        return when (classified.category) {
            com.lingce.cleanguard.model.FileCategory.TMP -> settings.autoCleanTmp && delete(classified)
            com.lingce.cleanguard.model.FileCategory.AI_GENERATED_USELESS ->
                settings.autoCleanAiGenerated && delete(classified)
            com.lingce.cleanguard.model.FileCategory.AI_CONFIG,
            com.lingce.cleanguard.model.FileCategory.PROJECT_CONFIG,
            -> settings.autoExcludeConfig && ExcludeService(project).exclude(classified)
            com.lingce.cleanguard.model.FileCategory.SUSPICIOUS -> false
            else -> false
        }
    }

    private fun findVirtualFile(relativePath: String): VirtualFile? {
        val root = project.basePath ?: return null
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(root, relativePath))
    }

    companion object {
        fun forProject(project: Project): CleanupService = CleanupService(project)
    }
}
