package com.lingce.cleankeeper.service

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.lingce.cleankeeper.model.FileCategory
import com.lingce.cleankeeper.model.FileClassification
import com.lingce.cleankeeper.settings.CleanKeeperSettings
import com.lingce.cleankeeper.ui.CleanKeeperToolWindowFactory
import java.util.concurrent.ConcurrentHashMap

data class TrackedFile(
    val file: VirtualFile,
    val relativePath: String,
    val classification: FileClassification,
    val timestamp: Long = System.currentTimeMillis(),
)

@Service(Service.Level.PROJECT)
class CleanupService(private val project: Project) {

    private val trackedFiles = ConcurrentHashMap<String, TrackedFile>()

    fun scanAndHandle(file: VirtualFile, manual: Boolean = false): FileClassification {
        if (!CleanKeeperSettings.getInstance().enabled) {
            return FileClassification(FileCategory.SAFE, 1.0, "插件已禁用", com.lingce.cleankeeper.model.ClassificationSource.MANUAL)
        }

        if (file.isDirectory || isInsideIgnoredArea(file)) {
            return FileClassification(FileCategory.SAFE, 1.0, "目录或已忽略区域", com.lingce.cleankeeper.model.ClassificationSource.HEURISTIC)
        }

        val classifier = FileClassifierService.getInstance(project)
        val classification = classifier.classify(file)
        val relativePath = classifier.getRelativePath(file)

        when (classification.category) {
            FileCategory.TMP_FILE -> handleTmp(file, relativePath, classification, manual)
            FileCategory.AI_GENERATED_USELESS -> handleAiUseless(file, relativePath, classification, manual)
            FileCategory.PROJECT_CONFIG, FileCategory.AI_CONFIG -> handleConfig(file, relativePath, classification)
            FileCategory.SUSPICIOUS -> handleSuspicious(file, relativePath, classification)
            FileCategory.SAFE -> trackedFiles.remove(relativePath)
        }

        refreshToolWindow()
        return classification
    }

    fun scanProject(): List<TrackedFile> {
        val base = project.basePath ?: return emptyList()
        val root = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(base) ?: return emptyList()
        val results = mutableListOf<TrackedFile>()
        collectAndScan(root, results, 0)
        refreshToolWindow()
        return results
    }

    private fun collectAndScan(dir: VirtualFile, results: MutableList<TrackedFile>, depth: Int) {
        if (depth > 8 || isInsideIgnoredArea(dir)) return
        for (child in dir.children) {
            if (child.isDirectory) {
                if (!shouldSkipDir(child.name)) {
                    collectAndScan(child, results, depth + 1)
                }
            } else {
                val classification = scanAndHandle(child)
                if (classification.category != FileCategory.SAFE) {
                    results.add(
                        TrackedFile(child, FileClassifierService.getInstance(project).getRelativePath(child), classification)
                    )
                }
            }
        }
    }

    private fun handleTmp(file: VirtualFile, path: String, classification: FileClassification, manual: Boolean) {
        val settings = CleanKeeperSettings.getInstance()
        if (settings.autoDeleteTmp && !manual) {
            if (QuarantineManager.deleteFile(file)) {
                notify("已自动删除临时文件: $path", NotificationType.INFORMATION)
                trackedFiles.remove(path)
                return
            }
        }
        track(file, path, classification)
        notifyWithActions(file, path, classification, "检测到临时文件")
    }

    private fun handleAiUseless(file: VirtualFile, path: String, classification: FileClassification, manual: Boolean) {
        val settings = CleanKeeperSettings.getInstance()
        if (settings.autoDeleteAiUseless && !manual) {
            if (QuarantineManager.deleteFile(file)) {
                notify("已自动删除无用 AI 产物: $path", NotificationType.INFORMATION)
                trackedFiles.remove(path)
                return
            }
        }
        track(file, path, classification)
        notifyWithActions(file, path, classification, "检测到无用 AI 产物")
    }

    private fun handleConfig(file: VirtualFile, path: String, classification: FileClassification) {
        val settings = CleanKeeperSettings.getInstance()
        if (settings.autoAddToIgnore) {
            val (git, _) = IgnoreManager.addToIgnore(project, path)
            if (git) {
                notify("已将配置类文件加入 .gitignore: $path", NotificationType.INFORMATION)
            }
        }
        track(file, path, classification)
    }

    private fun handleSuspicious(file: VirtualFile, path: String, classification: FileClassification) {
        track(file, path, classification)
        notifyWithActions(file, path, classification, "检测到可疑文件")
    }

    fun quarantine(file: VirtualFile): Boolean {
        val path = FileClassifierService.getInstance(project).getRelativePath(file)
        val result = QuarantineManager.quarantine(project, file)
        if (result != null) {
            trackedFiles.remove(path)
            notify("已隔离转存: $path → ${result.name}", NotificationType.INFORMATION)
            refreshToolWindow()
            return true
        }
        notify("隔离转存失败: $path", NotificationType.ERROR)
        return false
    }

    fun delete(file: VirtualFile): Boolean {
        val path = FileClassifierService.getInstance(project).getRelativePath(file)
        val ok = QuarantineManager.deleteFile(file)
        if (ok) {
            trackedFiles.remove(path)
            notify("已删除: $path", NotificationType.INFORMATION)
            refreshToolWindow()
        } else {
            notify("删除失败: $path", NotificationType.ERROR)
        }
        return ok
    }

    fun addToIgnore(file: VirtualFile): Boolean {
        val path = FileClassifierService.getInstance(project).getRelativePath(file)
        val (git, _) = IgnoreManager.addToIgnore(project, path)
        if (git) {
            notify("已加入 Ignore/Exclude: $path", NotificationType.INFORMATION)
        }
        return git
    }

    fun getTrackedFiles(): List<TrackedFile> = trackedFiles.values.sortedByDescending { it.timestamp }

    private fun track(file: VirtualFile, path: String, classification: FileClassification) {
        trackedFiles[path] = TrackedFile(file, path, classification)
    }

    private fun notify(message: String, type: NotificationType) {
        if (!CleanKeeperSettings.getInstance().showNotifications) return
        NotificationGroupManager.getInstance()
            .getNotificationGroup("AI File CleanKeeper")
            .createNotification(message, type)
            .notify(project)
    }

    private fun notifyWithActions(
        file: VirtualFile,
        path: String,
        classification: FileClassification,
        title: String,
    ) {
        if (!CleanKeeperSettings.getInstance().showNotifications) return
        val message = "$title: $path (${classification.category.displayName}) — ${classification.reason}"
        NotificationGroupManager.getInstance()
            .getNotificationGroup("AI File CleanKeeper")
            .createNotification(message, NotificationType.WARNING)
            .addAction(NotificationAction.createSimple("隔离转存") {
                ApplicationManager.getApplication().invokeLater { quarantine(file) }
            })
            .addAction(NotificationAction.createSimple("删除") {
                ApplicationManager.getApplication().invokeLater { delete(file) }
            })
            .addAction(NotificationAction.createSimple("加入 Ignore") {
                ApplicationManager.getApplication().invokeLater { addToIgnore(file) }
            })
            .notify(project)
    }

    private fun refreshToolWindow() {
        ApplicationManager.getApplication().invokeLater {
            CleanKeeperToolWindowFactory.refresh(project)
        }
    }

    private fun isInsideIgnoredArea(file: VirtualFile): Boolean {
        val path = file.path.replace('\\', '/')
        val ignored = listOf("/.git/", "/node_modules/", "/.cleankeeper/quarantine/", "/build/", "/dist/", "/.gradle/", "/out/")
        return ignored.any { path.contains(it) }
    }

    private fun shouldSkipDir(name: String): Boolean {
        return name in setOf(".git", "node_modules", "build", "dist", ".gradle", "out", ".idea", "target")
    }

    companion object {
        fun getInstance(project: Project): CleanupService = project.getService(CleanupService::class.java)
    }
}
