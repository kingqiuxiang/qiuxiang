package com.lingce.aicleaner.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.lingce.aicleaner.settings.AiCleanerSettings
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime

/**
 * 真正执行清理动作的服务：删除 / 隔离转存 / 加入 ignore / 标记 exclude。
 * 所有 VFS 写操作均在 WriteCommandAction 内执行。
 */
class CleanupService(private val project: Project) {

    private val settings get() = AiCleanerSettings.getInstance()

    /** 删除文件。开启 deleteToQuarantine 时实际转存到隔离目录。 */
    fun delete(file: VirtualFile): Boolean {
        if (settings.state.deleteToQuarantine) {
            return quarantine(file)
        }
        return runWrite("AI Cleaner: 删除") {
            file.delete(REQUESTOR)
            true
        }
    }

    /** 强制直接删除（不进隔离区），用于可疑文件的“一键删除”。 */
    fun forceDelete(file: VirtualFile): Boolean = runWrite("AI Cleaner: 删除") {
        file.delete(REQUESTOR)
        true
    }

    /** 转存到隔离目录，保留相对路径结构。 */
    fun quarantine(file: VirtualFile, targetDirOverride: String? = null): Boolean {
        val rel = relativePath(file)
        val quarantineRoot = resolveQuarantineDir(targetDirOverride)
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val dest = quarantineRoot.resolve(stamp).resolve(rel)

        return try {
            val src = Paths.get(file.path)
            Files.createDirectories(dest.parent)
            Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING)
            refresh(quarantineRoot)
            VfsUtil.markDirtyAndRefresh(false, true, true, file.parent)
            true
        } catch (e: Exception) {
            thisLogger().warn("quarantine failed for ${file.path}: ${e.message}")
            false
        }
    }

    /**
     * 加入忽略/排除：
     * 1) 把相对路径写入项目根 .gitignore（保证 git 干净）；
     * 2) 若为目录，则在所属模块中标记为 excluded（IDE 不索引）。
     */
    fun ignoreAndExclude(file: VirtualFile): Boolean {
        val okIgnore = addToGitignore(file)
        if (file.isDirectory) {
            markExcluded(file)
        }
        return okIgnore
    }

    fun addToGitignore(file: VirtualFile): Boolean {
        val baseDir = ProjectPaths.baseDir(project) ?: return false
        val rel = relativePath(file).let { if (file.isDirectory) "$it/" else it }
        return runWrite("AI Cleaner: 加入 .gitignore") {
            val gitignore = baseDir.findChild(".gitignore")
                ?: baseDir.createChildData(REQUESTOR, ".gitignore")
            val text = VfsUtilCore.loadText(gitignore)
            val lines = text.split('\n').map { it.trim() }.toMutableSet()
            if (rel !in lines && rel.trimEnd('/') !in lines) {
                val newText = buildString {
                    append(text.trimEnd('\n'))
                    if (text.isNotBlank()) append('\n')
                    append("# added by AI File Cleaner\n")
                    append(rel).append('\n')
                }
                VfsUtil.saveText(gitignore, newText)
            }
            true
        }
    }

    fun markExcluded(dir: VirtualFile): Boolean {
        val module = ModuleUtilCore.findModuleForFile(dir, project) ?: return false
        return try {
            ApplicationManager.getApplication().invokeAndWait {
                ModuleRootModificationUtil.updateModel(module) { model ->
                    val contentRoot = ModuleRootManager.getInstance(module).contentRoots
                        .firstOrNull { VfsUtilCore.isAncestor(it, dir, false) }
                    val entry = model.contentEntries.firstOrNull {
                        it.file != null && contentRoot != null && it.file == contentRoot
                    } ?: model.contentEntries.firstOrNull()
                    entry?.addExcludeFolder(dir)
                }
            }
            true
        } catch (e: Exception) {
            thisLogger().warn("markExcluded failed for ${dir.path}: ${e.message}")
            false
        }
    }

    private fun resolveQuarantineDir(override: String?): Path {
        val configured = override?.takeIf { it.isNotBlank() }
            ?: settings.state.quarantineDir.takeIf { it.isNotBlank() }
        val base = project.basePath ?: System.getProperty("user.home")
        return if (configured != null) Paths.get(configured)
        else Paths.get(base, ".ai-cleaner-quarantine")
    }

    private fun refresh(path: Path) {
        LocalFileSystem.getInstance().refreshAndFindFileByPath(path.toString())
    }

    private fun relativePath(file: VirtualFile): String {
        val baseVf = ProjectPaths.baseDir(project)
        return if (baseVf != null) VfsUtilCore.getRelativePath(file, baseVf) ?: file.name else file.name
    }

    private fun <T> runWrite(title: String, block: () -> T): T {
        return WriteCommandAction.writeCommandAction(project)
            .withName(title)
            .compute<T, RuntimeException> { block() }
    }

    companion object {
        /** VFS 写操作的发起者标记。 */
        private val REQUESTOR = Any()
    }
}
