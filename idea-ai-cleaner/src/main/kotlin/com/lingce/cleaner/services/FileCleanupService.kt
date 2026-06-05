package com.lingce.cleaner.services

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.util.concurrency.AppExecutorUtil
import com.lingce.cleaner.api.AiFileDetectorClient
import com.lingce.cleaner.model.FileCategory
import com.lingce.cleaner.model.FileClassification
import com.lingce.cleaner.settings.CleanerConfigurable
import com.lingce.cleaner.settings.CleanerSettingsState
import com.lingce.cleaner.util.FilePatterns
import java.nio.charset.MalformedInputException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArraySet
import java.util.stream.Collectors
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

@Service(Service.Level.PROJECT)
class FileCleanupService(private val project: Project) : Disposable {
    private val log = Logger.getInstance(FileCleanupService::class.java)
    private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("ai-file-cleaner", 1)
    private val apiClient = AiFileDetectorClient()
    private val suspiciousFiles = CopyOnWriteArraySet<Path>()

    init {
        val connection = project.messageBus.connect(this)
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: MutableList<out VFileEvent>) {
                if (project.isDisposed) return
                val changedPaths = events.mapNotNull { extractChangedPath(it) }
                    .map { Paths.get(it) }
                    .filter { path -> Files.exists(path) && Files.isRegularFile(path) }
                if (changedPaths.isEmpty()) return
                executor.execute { processChangedFiles(changedPaths) }
            }
        })
    }

    fun initializeAndScan() {
        scanProjectAndApplyCleanup(trigger = "startup", showSummary = true)
    }

    fun scanProjectAndApplyCleanup(trigger: String, showSummary: Boolean) {
        val basePath = project.basePath ?: return
        val root = Paths.get(basePath)
        if (!root.exists() || !root.isDirectory()) return

        executor.execute {
            var deletedTmp = 0
            var deletedAi = 0
            var movedConfig = 0
            val newlySuspicious = mutableListOf<Path>()
            val settings = CleanerSettingsState.getInstance().state

            Files.walk(root).use { stream ->
                val files = stream
                    .filter { path -> Files.isRegularFile(path) }
                    .filter { path -> isManagedByCleaner(root, path).not() }
                    .filter { path -> isIgnoredTraversalPath(root, path).not() }
                    .collect(Collectors.toList())

                for (path in files) {
                    val classification = classify(path, root, settings)
                    when (classification.category) {
                        FileCategory.TMP -> {
                            if (settings.autoDeleteTmpFiles && deleteFile(path)) {
                                deletedTmp += 1
                            }
                        }

                        FileCategory.AI_GENERATED -> {
                            if (settings.autoDeleteAiFiles && deleteFile(path)) {
                                deletedAi += 1
                            }
                        }

                        FileCategory.PROJECT_CONFIG -> {
                            if (moveOrMarkConfig(path, root, "ignore")) {
                                movedConfig += 1
                            }
                        }

                        FileCategory.AI_CONFIG -> {
                            if (moveOrMarkConfig(path, root, "exclude")) {
                                movedConfig += 1
                            }
                        }

                        FileCategory.SUSPICIOUS -> newlySuspicious += path
                        FileCategory.SAFE -> Unit
                    }
                }
            }

            if (newlySuspicious.isNotEmpty()) {
                suspiciousFiles.addAll(newlySuspicious)
            }
            VirtualFileManager.getInstance().asyncRefresh(null)

            if (showSummary) {
                showScanSummary(trigger, deletedTmp, deletedAi, movedConfig, newlySuspicious.size)
            } else if (newlySuspicious.isNotEmpty()) {
                showSuspiciousNotification(newlySuspicious.size)
            }
        }
    }

    fun archiveSuspiciousFiles() {
        val settings = CleanerSettingsState.getInstance().state
        if (settings.suspiciousArchiveDir.isBlank()) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("AI File Cleaner")
                .createNotification("请先在设置中配置“可疑文件转存目录”", NotificationType.WARNING)
                .notify(project)
            return
        }

        executor.execute {
            val root = project.basePath?.let { Paths.get(it) } ?: return@execute
            val archiveRoot = Paths.get(settings.suspiciousArchiveDir)
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            val sessionRoot = archiveRoot.resolve("${project.name}-suspicious-$timestamp")
            var movedCount = 0

            for (source in suspiciousFiles.toList()) {
                if (!Files.exists(source) || !Files.isRegularFile(source)) continue
                val targetRelative = if (source.startsWith(root)) root.relativize(source) else Paths.get(source.fileName.toString())
                val target = uniqueTarget(sessionRoot.resolve(targetRelative))
                try {
                    Files.createDirectories(target.parent)
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
                    suspiciousFiles.remove(source)
                    movedCount += 1
                } catch (ex: Exception) {
                    log.warn("Failed to archive suspicious file ${source.fileName}: ${ex.message}")
                }
            }

            VirtualFileManager.getInstance().asyncRefresh(null)
            NotificationGroupManager.getInstance()
                .getNotificationGroup("AI File Cleaner")
                .createNotification("已转存 $movedCount 个可疑文件到 $sessionRoot", NotificationType.INFORMATION)
                .notify(project)
        }
    }

    fun deleteSuspiciousFiles() {
        executor.execute {
            var deleted = 0
            for (path in suspiciousFiles.toList()) {
                if (deleteFile(path)) {
                    suspiciousFiles.remove(path)
                    deleted += 1
                }
            }
            VirtualFileManager.getInstance().asyncRefresh(null)
            NotificationGroupManager.getInstance()
                .getNotificationGroup("AI File Cleaner")
                .createNotification("已删除 $deleted 个可疑文件", NotificationType.INFORMATION)
                .notify(project)
        }
    }

    private fun processChangedFiles(paths: List<Path>) {
        val root = project.basePath?.let { Paths.get(it) } ?: return
        val settings = CleanerSettingsState.getInstance().state
        val newlySuspicious = mutableListOf<Path>()

        for (path in paths) {
            if (!path.startsWith(root) || isManagedByCleaner(root, path)) continue
            if (isIgnoredTraversalPath(root, path)) continue
            val classification = classify(path, root, settings)
            when (classification.category) {
                FileCategory.TMP -> if (settings.autoDeleteTmpFiles) deleteFile(path)
                FileCategory.AI_GENERATED -> if (settings.autoDeleteAiFiles) deleteFile(path)
                FileCategory.PROJECT_CONFIG -> moveOrMarkConfig(path, root, "ignore")
                FileCategory.AI_CONFIG -> moveOrMarkConfig(path, root, "exclude")
                FileCategory.SUSPICIOUS -> newlySuspicious += path
                FileCategory.SAFE -> Unit
            }
        }

        if (newlySuspicious.isNotEmpty()) {
            suspiciousFiles.addAll(newlySuspicious)
            showSuspiciousNotification(newlySuspicious.size)
        }
    }

    private fun classify(path: Path, root: Path, settings: CleanerSettingsState.State): FileClassification {
        val relativePath = normalizeRelative(root, path)
        val size = runCatching { Files.size(path) }.getOrDefault(0L)

        if (FilePatterns.isTmpFile(path)) {
            return FileClassification(FileCategory.TMP, "tmp/temp pattern")
        }
        if (FilePatterns.isProjectConfigFile(relativePath)) {
            return FileClassification(FileCategory.PROJECT_CONFIG, "project config marker")
        }
        if (FilePatterns.isAiConfigFile(relativePath)) {
            return FileClassification(FileCategory.AI_CONFIG, "ai config marker")
        }

        val sampleContent = readSample(path)
        if (FilePatterns.looksAiGenerated(path, sampleContent)) {
            return FileClassification(FileCategory.AI_GENERATED, "AI-like markers in file name/content", 0.8)
        }

        if (settings.useApiDetection) {
            val apiResult = apiClient.detect(relativePath, sampleContent, settings)
            if (apiResult != null) {
                if (apiResult.label == "AI") {
                    return FileClassification(FileCategory.AI_GENERATED, apiResult.reason, apiResult.confidence)
                }
                if (apiResult.label == "SUSPICIOUS") {
                    return FileClassification(FileCategory.SUSPICIOUS, apiResult.reason, apiResult.confidence)
                }
            }
        }

        if (FilePatterns.looksSuspicious(path, size)) {
            return FileClassification(FileCategory.SUSPICIOUS, "suspicious extension/name/size", 0.7)
        }

        return FileClassification(FileCategory.SAFE, "no rule matched")
    }

    private fun moveOrMarkConfig(source: Path, root: Path, folderName: String): Boolean {
        val relative = root.relativize(source)
        if (isEssentialConfigPath(relative)) {
            return appendToManifest(root, folderName, relative.toString())
        }
        val targetRoot = root.resolve(".idea-ai-cleaner").resolve(folderName)
        val target = uniqueTarget(targetRoot.resolve(relative))
        return try {
            Files.createDirectories(target.parent)
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
            appendToManifest(root, folderName, relative.toString())
            true
        } catch (ex: Exception) {
            log.warn("Failed to move config file ${source.fileName}: ${ex.message}")
            false
        }
    }

    private fun appendToManifest(root: Path, folderName: String, relativePath: String): Boolean {
        return try {
            val manifest = root.resolve(".idea-ai-cleaner").resolve(folderName).resolve("manifest.txt")
            Files.createDirectories(manifest.parent)
            Files.writeString(
                manifest,
                "$relativePath${System.lineSeparator()}",
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND
            )
            true
        } catch (ex: Exception) {
            log.warn("Failed to append manifest for $relativePath: ${ex.message}")
            false
        }
    }

    private fun isEssentialConfigPath(relative: Path): Boolean {
        val normalized = relative.toString().replace('\\', '/').lowercase()
        return normalized.startsWith(".idea/") ||
            normalized.startsWith(".vscode/") ||
            normalized.startsWith(".cursor/")
    }

    private fun deleteFile(path: Path): Boolean {
        return try {
            Files.deleteIfExists(path)
        } catch (ex: Exception) {
            log.warn("Failed deleting ${path.fileName}: ${ex.message}")
            false
        }
    }

    private fun uniqueTarget(target: Path): Path {
        if (!Files.exists(target)) return target
        val fileName = target.fileName.toString()
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        var index = 1
        while (true) {
            val candidate = target.parent.resolve("$base-$index$ext")
            if (!Files.exists(candidate)) return candidate
            index += 1
        }
    }

    private fun isManagedByCleaner(root: Path, path: Path): Boolean {
        return path.startsWith(root.resolve(".idea-ai-cleaner"))
    }

    private fun isIgnoredTraversalPath(root: Path, path: Path): Boolean {
        val relative = normalizeRelative(root, path)
        return relative.startsWith(".git/") ||
            relative.startsWith("node_modules/") ||
            relative.startsWith("build/") ||
            relative.startsWith("dist/") ||
            relative.startsWith("out/")
    }

    private fun normalizeRelative(root: Path, path: Path): String {
        return root.relativize(path).toString().replace('\\', '/')
    }

    private fun readSample(path: Path, limit: Int = 5000): String {
        val size = runCatching { Files.size(path) }.getOrDefault(0L)
        if (size > 2 * 1024 * 1024) return ""
        return try {
            val text = Files.readString(path)
            if (text.length <= limit) text else text.substring(0, limit)
        } catch (_: MalformedInputException) {
            ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractChangedPath(event: VFileEvent): String? {
        return when (event) {
            is VFileCreateEvent -> event.path
            is VFileContentChangeEvent -> event.path
            is VFileMoveEvent -> event.newPath
            is VFileCopyEvent -> "${event.newParent.path}/${event.newChildName}"
            else -> null
        }
    }

    private fun showScanSummary(trigger: String, deletedTmp: Int, deletedAi: Int, movedConfig: Int, suspicious: Int) {
        val content = buildString {
            append("触发方式: $trigger\n")
            append("删除 tmp 文件: $deletedTmp\n")
            append("删除 AI 文件: $deletedAi\n")
            append("转移/登记配置文件: $movedConfig\n")
            append("可疑文件: $suspicious")
        }

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("AI File Cleaner")
            .createNotification("AI File Cleaner 扫描完成", content, NotificationType.INFORMATION)

        if (suspicious > 0) {
            attachSuspiciousActions(notification)
        }

        notification.notify(project)
    }

    private fun showSuspiciousNotification(newCount: Int) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("AI File Cleaner")
            .createNotification("发现 $newCount 个可疑文件", "支持一键转存或一键删除。", NotificationType.WARNING)
        attachSuspiciousActions(notification)
        notification.notify(project)
    }

    private fun attachSuspiciousActions(notification: com.intellij.notification.Notification) {
        notification.addAction(NotificationAction.createSimple("一键转存可疑文件") { archiveSuspiciousFiles() })
        notification.addAction(NotificationAction.createSimple("一键删除可疑文件") { deleteSuspiciousFiles() })
        notification.addAction(NotificationAction.createSimple("打开插件设置") {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, CleanerConfigurable::class.java)
        })
    }

    override fun dispose() {
        // no-op
    }
}
