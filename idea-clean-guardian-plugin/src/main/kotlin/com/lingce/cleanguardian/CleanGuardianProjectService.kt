package com.lingce.cleanguardian

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class CleanGuardianProjectService(private val project: Project) : Disposable {
    private val logger = Logger.getInstance(CleanGuardianProjectService::class.java)
    private val classifier = FileClassifier()
    private val exclusionRegistry = ExclusionRegistry(project)
    private val isFullScanRunning = AtomicBoolean(false)

    init {
        project.messageBus.connect(this).subscribe(
            com.intellij.openapi.vfs.VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: MutableList<out VFileEvent>) {
                    val settings = CleanGuardianSettingsService.getInstance().state
                    if (!settings.enableRealtimeMonitor) return
                    val localPaths = events.asSequence()
                        .mapNotNull { it.path }
                        .mapNotNull { runCatching { Path.of(it) }.getOrNull() }
                        .filter { Files.exists(it) && Files.isRegularFile(it) }
                        .toList()
                    if (localPaths.isNotEmpty()) {
                        processRealtimePaths(localPaths)
                    }
                }
            }
        )
    }

    fun onProjectOpened() {
        val settings = CleanGuardianSettingsService.getInstance().state
        if (settings.scanOnProjectOpen) {
            requestFullScan(trigger = "startup", manual = false)
        }
    }

    fun triggerManualScan() {
        requestFullScan(trigger = "manual", manual = true)
    }

    override fun dispose() = Unit

    private fun requestFullScan(trigger: String, manual: Boolean) {
        if (!isFullScanRunning.compareAndSet(false, true)) {
            if (manual) {
                notifyInfo("Clean Guardian", "扫描已在进行中，请稍候。")
            }
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val result = scanAllProjectFiles()
                if (manual) {
                    notifyInfo(
                        "Scan Completed",
                        "已处理 ${result.scanned} 个文件，删除 ${result.deleted} 个，标记配置 ${result.excludedMarked} 个，可疑 ${result.suspicious.size} 个。"
                    )
                }
                if (result.suspicious.isNotEmpty()) {
                    notifySuspicious(result.suspicious)
                }
                logger.info("Clean Guardian full scan finished: trigger=$trigger result=$result")
            } catch (t: Throwable) {
                logger.warn("Clean Guardian full scan failed", t)
                if (manual) {
                    notifyError("Clean Guardian", "扫描失败：${t.message ?: "未知异常"}")
                }
            } finally {
                isFullScanRunning.set(false)
                refreshProjectFiles()
            }
        }
    }

    private fun processRealtimePaths(paths: List<Path>) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val settings = CleanGuardianSettingsService.getInstance().state
            val basePath = project.basePath?.let { Path.of(it) } ?: return@executeOnPooledThread
            val suspicious = mutableListOf<Path>()
            val deleted = mutableListOf<Path>()
            val markExclude = mutableListOf<String>()

            for (path in paths.distinct()) {
                val relative = toRelative(basePath, path) ?: continue
                if (shouldSkip(relative) || exclusionRegistry.isExcluded(relative)) continue

                val result = classifier.classify(path, relative, settings)
                when (result.category) {
                    FileCategory.TMP -> if (settings.autoDeleteTmpFiles) {
                        if (safeDelete(path)) deleted.add(path)
                    }
                    FileCategory.AI_GARBAGE -> if (settings.autoDeleteAiGarbageFiles) {
                        if (safeDelete(path)) deleted.add(path)
                    }
                    FileCategory.PROJECT_CONFIG, FileCategory.AI_CONFIG -> markExclude.add(relative)
                    FileCategory.SUSPICIOUS -> suspicious.add(path)
                    else -> Unit
                }
            }

            if (markExclude.isNotEmpty()) {
                exclusionRegistry.addPaths(markExclude)
            }
            if (suspicious.isNotEmpty()) {
                notifySuspicious(suspicious)
            }
            if (deleted.isNotEmpty()) {
                notifyInfo("Clean Guardian", "实时清理完成：已删除 ${deleted.size} 个文件。")
            }
            refreshProjectFiles()
        }
    }

    private fun scanAllProjectFiles(): ScanResult {
        val settings = CleanGuardianSettingsService.getInstance().state
        val basePath = project.basePath?.let { Path.of(it) } ?: return ScanResult()

        val toDelete = mutableListOf<Path>()
        val toExclude = mutableListOf<String>()
        val suspicious = mutableListOf<Path>()
        var scanned = 0

        Files.walk(basePath).use { stream ->
            stream.filter { Files.isRegularFile(it) }.forEach { path ->
                val relative = toRelative(basePath, path) ?: return@forEach
                if (shouldSkip(relative)) return@forEach
                if (exclusionRegistry.isExcluded(relative)) return@forEach

                scanned++
                val result = classifier.classify(path, relative, settings)
                when (result.category) {
                    FileCategory.TMP -> if (settings.autoDeleteTmpFiles) toDelete.add(path)
                    FileCategory.AI_GARBAGE -> if (settings.autoDeleteAiGarbageFiles) toDelete.add(path)
                    FileCategory.PROJECT_CONFIG, FileCategory.AI_CONFIG -> toExclude.add(relative)
                    FileCategory.SUSPICIOUS -> suspicious.add(path)
                    else -> Unit
                }
            }
        }

        if (toExclude.isNotEmpty()) {
            exclusionRegistry.addPaths(toExclude)
        }

        var deletedCount = 0
        toDelete.distinct().forEach { path ->
            if (safeDelete(path)) deletedCount++
        }

        return ScanResult(
            scanned = scanned,
            deleted = deletedCount,
            excludedMarked = toExclude.distinct().size,
            suspicious = suspicious.distinct()
        )
    }

    private fun notifySuspicious(suspiciousFiles: List<Path>) {
        val basePath = project.basePath?.let { Path.of(it) } ?: return
        val preview = suspiciousFiles.take(8)
            .mapNotNull { toRelative(basePath, it) }
            .joinToString("<br/>")
        val summary = if (suspiciousFiles.size > 8) "$preview<br/>..." else preview

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Clean Guardian Notifications")
            .createNotification(
                "发现可疑文件 ${suspiciousFiles.size} 个",
                "以下文件建议处置：<br/>$summary",
                NotificationType.WARNING
            )

        notification.addAction(NotificationAction.createSimple("一键转存可疑文件") {
            archiveSuspiciousFiles(suspiciousFiles)
            it.notification.expire()
        })
        notification.addAction(NotificationAction.createSimple("一键删除可疑文件") {
            val deleted = suspiciousFiles.count { path -> safeDelete(path) }
            notifyInfo("Clean Guardian", "已删除可疑文件 $deleted 个。")
            refreshProjectFiles()
            it.notification.expire()
        })

        notification.notify(project)
    }

    private fun archiveSuspiciousFiles(suspiciousFiles: List<Path>) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val settings = CleanGuardianSettingsService.getInstance().state
            val basePath = project.basePath?.let { Path.of(it) } ?: return@executeOnPooledThread
            val quarantineRoot = settings.quarantineDirectory.ifBlank {
                System.getProperty("user.home") + "/.clean-guardian-quarantine"
            }
            val sessionDirName = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            val destinationRoot = Path.of(quarantineRoot, project.name, sessionDirName)

            var movedCount = 0
            suspiciousFiles.distinct().forEach { source ->
                if (!Files.exists(source)) return@forEach
                val relative = toRelative(basePath, source) ?: return@forEach
                val target = destinationRoot.resolve(relative)
                runCatching {
                    Files.createDirectories(target.parent)
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
                }.onSuccess {
                    movedCount++
                }.onFailure {
                    logger.warn("Failed to move suspicious file: $source", it)
                }
            }

            notifyInfo("Clean Guardian", "可疑文件转存完成：$movedCount 个，目录：$destinationRoot")
            refreshProjectFiles()
        }
    }

    private fun safeDelete(path: Path): Boolean {
        return runCatching {
            Files.deleteIfExists(path)
        }.onFailure {
            logger.warn("Failed to delete file: $path", it)
        }.getOrDefault(false)
    }

    private fun toRelative(basePath: Path, file: Path): String? {
        return runCatching { basePath.relativize(file).toString().replace('\\', '/') }.getOrNull()
    }

    private fun shouldSkip(relativePath: String): Boolean {
        return relativePath.startsWith(".git/") ||
            relativePath.startsWith(".idea/") ||
            relativePath.startsWith(".clean-guardian/") ||
            relativePath.startsWith("node_modules/") ||
            relativePath.startsWith("build/") ||
            relativePath.startsWith("out/") ||
            relativePath.startsWith("target/")
    }

    private fun refreshProjectFiles() {
        ApplicationManager.getApplication().invokeLater {
            project.baseDir?.refresh(true, true)
        }
    }

    private fun notifyInfo(title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Clean Guardian Notifications")
            .createNotification(title, content, NotificationType.INFORMATION)
            .notify(project)
    }

    private fun notifyError(title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Clean Guardian Notifications")
            .createNotification(title, content, NotificationType.ERROR)
            .notify(project)
    }

    private data class ScanResult(
        val scanned: Int = 0,
        val deleted: Int = 0,
        val excludedMarked: Int = 0,
        val suspicious: List<Path> = emptyList()
    )
}
