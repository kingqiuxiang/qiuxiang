package com.lingce.aicleaner.listener

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.lingce.aicleaner.core.CleanupService
import com.lingce.aicleaner.core.FileClassifier
import com.lingce.aicleaner.core.Notifier
import com.lingce.aicleaner.model.CleanAction
import com.lingce.aicleaner.model.ClassificationResult
import com.lingce.aicleaner.model.FileCategory
import com.lingce.aicleaner.settings.AiCleanerSettings

/**
 * 启动后订阅 VFS 变更，对新导入/新建文件做即时分类与处理。
 */
class ImportWatcher : ProjectActivity {

    override suspend fun execute(project: Project) {
        val connection = ApplicationManager.getApplication().messageBus.connect(project)
        connection.subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val settings = AiCleanerSettings.getInstance()
                    if (!settings.state.watchImportedFiles) return

                    val candidates = events
                        .filter { it is VFileCreateEvent || it is VFileCopyEvent || it is VFileMoveEvent }
                        .mapNotNull { it.file }
                        .filter { it.isValid && !it.isDirectory && belongsToProject(project, it) }
                        .distinct()
                    if (candidates.isEmpty()) return

                    // 避免阻塞 VFS 回调线程
                    ApplicationManager.getApplication().executeOnPooledThread {
                        candidates.forEach { handle(project, it) }
                    }
                }
            },
        )
    }

    private fun handle(project: Project, file: VirtualFile) {
        if (!file.isValid) return
        val settings = AiCleanerSettings.getInstance()
        val result = try {
            // 监听场景下不实时调用 AI，避免对大量文件产生开销
            FileClassifier(project).classify(file, allowAi = false)
        } catch (e: Exception) {
            return
        }
        if (!result.actionable) return

        if (settings.state.autoClean && result.category != FileCategory.SUSPICIOUS) {
            autoClean(project, result)
        } else {
            suggest(project, result)
        }
    }

    private fun autoClean(project: Project, result: ClassificationResult) {
        val cleanup = CleanupService(project)
        ApplicationManager.getApplication().invokeLater {
            val ok = when (result.recommendedAction) {
                CleanAction.DELETE -> cleanup.delete(result.file)
                CleanAction.IGNORE_EXCLUDE -> cleanup.ignoreAndExclude(result.file)
                else -> false
            }
            if (ok) {
                Notifier.info(
                    project,
                    "已自动处理：${result.category.displayName}",
                    "${result.file.name} → ${result.recommendedAction.displayName}（${result.reason}）",
                )
            }
        }
    }

    private fun suggest(project: Project, result: ClassificationResult) {
        val file = result.file
        val cleanup = CleanupService(project)
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP)
            .createNotification(
                "检测到${result.category.displayName}",
                "${file.name}：${result.reason}",
                NotificationType.INFORMATION,
            )

        notification.addAction(action("转存隔离") { cleanup.quarantine(file) })
        notification.addAction(action("删除") { cleanup.delete(file) })
        notification.addAction(action("忽略/排除") { cleanup.ignoreAndExclude(file) })
        notification.notify(project)
    }

    private fun action(text: String, op: () -> Unit): NotificationAction =
        object : NotificationAction(text) {
            override fun actionPerformed(e: AnActionEvent, notification: com.intellij.notification.Notification) {
                ApplicationManager.getApplication().invokeLater {
                    op()
                    notification.expire()
                }
            }
        }

    private fun belongsToProject(project: Project, file: VirtualFile): Boolean {
        val base = project.basePath ?: return false
        val path = file.path
        if (!path.startsWith(base)) return false
        return SKIP_SEGMENTS.none { "/$it/" in path || path.endsWith("/$it") }
    }

    companion object {
        private const val GROUP = "AI File Cleaner"
        private val SKIP_SEGMENTS = setOf(
            ".git", "node_modules", ".gradle", "build", "dist", "out", "target", ".idea",
            ".ai-cleaner-quarantine",
        )
    }
}
