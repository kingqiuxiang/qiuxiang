package com.lingce.aifilecleaner.service

import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import com.lingce.aifilecleaner.model.FileCategory
import com.lingce.aifilecleaner.model.ScanResult
import com.lingce.aifilecleaner.settings.CleanerSettingsState
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

@Service(Service.Level.PROJECT)
class FileCleanupService(private val project: Project) {
    private val classifier = AiFileClassifier()

    @Volatile
    private var lastResult: ScanResult = ScanResult()

    fun lastResult(): ScanResult = lastResult

    fun runScan(trigger: String = "manual", onFinished: ((ScanResult) -> Unit)? = null) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = performScan()
            lastResult = result
            notify("扫描完成（$trigger）", summarize(result), NotificationType.INFORMATION)
            onFinished?.invoke(result)
        }
    }

    fun archiveSuspiciousFiles(): Int {
        val settings = service<CleanerSettingsState>().state
        val basePath = project.basePath ?: return 0
        val projectRoot = Paths.get(basePath)
        val archiveRoot = projectRoot.resolve(settings.suspiciousArchiveDir).normalize()
        var moved = 0

        lastResult.suspiciousFiles.forEach { suspiciousPath ->
            moved += runCatching {
                moveFilePreservingRelativePath(suspiciousPath, projectRoot, archiveRoot)
                1
            }.getOrDefault(0)
        }
        refreshIo(projectRoot)
        notify("可疑文件处理", "已转存 $moved 个可疑文件到 ${archiveRoot.toAbsolutePath()}", NotificationType.INFORMATION)
        return moved
    }

    fun deleteSuspiciousFiles(): Int {
        val basePath = project.basePath ?: return 0
        val projectRoot = Paths.get(basePath)
        var deleted = 0
        lastResult.suspiciousFiles.forEach { path ->
            deleted += runCatching {
                if (Files.deleteIfExists(path)) 1 else 0
            }.getOrDefault(0)
        }
        refreshIo(projectRoot)
        notify("可疑文件处理", "已删除 $deleted 个可疑文件", NotificationType.WARNING)
        return deleted
    }

    private fun performScan(): ScanResult {
        val basePath = project.basePath ?: return ScanResult()
        val projectRoot = Paths.get(basePath)
        val settings = service<CleanerSettingsState>().state
        val maxFileSizeBytes = settings.maxFileSizeKb.toLong() * 1024L

        val candidates = mutableListOf<Path>()
        ProjectFileIndex.getInstance(project).iterateContent { vFile ->
            if (!vFile.isDirectory) {
                candidates += Paths.get(vFile.path)
            }
            true
        }

        var scanned = 0
        var deletedTmp = 0
        var deletedAiJunk = 0
        var movedConfig = 0
        val suspicious = mutableListOf<Path>()
        val errors = mutableListOf<String>()

        val ignoreExcludeRoot = projectRoot.resolve(settings.ignoreExcludeDir).normalize()

        candidates.forEach { path ->
            try {
                if (!Files.exists(path)) return@forEach
                val relativePath = projectRoot.relativize(path).toString().replace('\\', '/')
                if (shouldSkip(relativePath, settings)) return@forEach

                scanned++
                val size = Files.size(path)
                if (size > maxFileSizeBytes) return@forEach
                val text = readTextSafely(path)
                val category = classifier.classify(path, relativePath, text, settings)

                when (category) {
                    FileCategory.TMP -> {
                        if (settings.autoDeleteTmpFiles && Files.deleteIfExists(path)) {
                            deletedTmp++
                        }
                    }

                    FileCategory.AI_JUNK -> {
                        if (settings.autoDeleteAiJunkFiles && Files.deleteIfExists(path)) {
                            deletedAiJunk++
                        }
                    }

                    FileCategory.CONFIG_OR_AI_CONFIG -> {
                        if (!path.startsWith(ignoreExcludeRoot)) {
                            moveFilePreservingRelativePath(path, projectRoot, ignoreExcludeRoot)
                            movedConfig++
                        }
                    }

                    FileCategory.SUSPICIOUS -> suspicious += path
                    FileCategory.SAFE -> Unit
                }
            } catch (e: Exception) {
                errors += "${path.fileName}: ${e.message ?: "unknown error"}"
            }
        }

        refreshIo(projectRoot)
        return ScanResult(
            scannedFiles = scanned,
            deletedTmpFiles = deletedTmp,
            deletedAiJunkFiles = deletedAiJunk,
            movedConfigFiles = movedConfig,
            suspiciousFiles = suspicious.toList(),
            errors = errors.toList()
        )
    }

    private fun shouldSkip(relativePath: String, settings: CleanerSettingsState.State): Boolean {
        val normalized = relativePath.replace('\\', '/')
        if (normalized.startsWith(".git/")) return true
        if (normalized.startsWith("node_modules/")) return true
        if (normalized.startsWith("build/")) return true
        if (normalized.startsWith("out/")) return true
        if (normalized.startsWith(settings.ignoreExcludeDir.trim('/'))) return true
        if (normalized.startsWith(settings.suspiciousArchiveDir.trim('/'))) return true
        return false
    }

    private fun readTextSafely(path: Path): String? {
        return runCatching {
            val bytes = Files.readAllBytes(path)
            if (bytes.any { it == 0.toByte() }) {
                null
            } else {
                String(bytes, StandardCharsets.UTF_8)
            }
        }.getOrNull()
    }

    private fun moveFilePreservingRelativePath(source: Path, projectRoot: Path, targetRoot: Path) {
        val relative = projectRoot.relativize(source)
        val target = targetRoot.resolve(relative)
        Files.createDirectories(target.parent)
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun summarize(result: ScanResult): String {
        return "扫描 ${result.scannedFiles} 个文件，删除 tmp ${result.deletedTmpFiles} 个，" +
            "删除 AI 垃圾 ${result.deletedAiJunkFiles} 个，归档配置 ${result.movedConfigFiles} 个，" +
            "可疑 ${result.suspiciousFiles.size} 个。"
    }

    private fun notify(title: String, content: String, type: NotificationType) {
        Notifications.Bus.notify(
            com.intellij.notification.Notification("AI File Cleaner", title, content, type),
            project
        )
    }

    private fun refreshIo(projectRoot: Path) {
        LocalFileSystem.getInstance().refreshIoFiles(listOf(projectRoot.toFile()), true, true, null)
    }
}
