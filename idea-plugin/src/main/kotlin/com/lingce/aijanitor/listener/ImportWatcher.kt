package com.lingce.aijanitor.listener

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.lingce.aijanitor.classify.ClassificationResult
import com.lingce.aijanitor.classify.RecommendedAction
import com.lingce.aijanitor.service.FileJanitorService
import com.lingce.aijanitor.settings.AiJanitorSettings
import com.lingce.aijanitor.ui.ScanResultDialog

/**
 * Watches the VFS for newly created/copied files. When import brings in files
 * that look like junk/temp/AI output, it surfaces a non-intrusive notification
 * offering to clean them up (or auto-cleans confident cases when configured).
 */
class ImportWatcher : BulkFileListener {

    private val skipDirNames = setOf(".git", ".hg", ".svn", "node_modules")

    override fun after(events: List<VFileEvent>) {
        val settings = AiJanitorSettings.getInstance()
        if (!settings.autoScanOnImport) return

        val created = events.mapNotNull { ev ->
            when (ev) {
                is VFileCreateEvent -> ev.file
                is VFileCopyEvent -> ev.findCreatedFile()
                else -> null
            }
        }.filter { it.isValid && !it.isDirectory && !inSkippedDir(it) }
        if (created.isEmpty()) return

        // Don't react to massive bulk operations (git checkout, build output, etc.).
        if (created.size > 200) return

        ApplicationManager.getApplication().invokeLater {
            val byProject = created.groupBy { ProjectLocator.getInstance().guessProjectForFile(it) }
            for ((project, files) in byProject) {
                if (project == null || project.isDisposed) continue
                scheduleScan(project, files)
            }
        }
    }

    private fun scheduleScan(project: com.intellij.openapi.project.Project, files: List<VirtualFile>) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "AI 文件清理：检查新增文件", true) {
            override fun run(indicator: ProgressIndicator) {
                val results = FileJanitorService.getInstance(project).scan(files, indicator)
                val actionable = results.filter { it.isActionable }
                if (actionable.isEmpty()) return
                ApplicationManager.getApplication().invokeLater {
                    notify(project, results, actionable)
                }
            }
        })
    }

    private fun notify(
        project: com.intellij.openapi.project.Project,
        all: List<ClassificationResult>,
        actionable: List<ClassificationResult>,
    ) {
        val settings = AiJanitorSettings.getInstance()
        if (settings.autoDeleteConfident) {
            val auto = actionable.filter { it.action == RecommendedAction.DELETE && it.confidence >= 0.85 }
            if (auto.isNotEmpty()) {
                val svc = FileJanitorService.getInstance(project)
                ProgressManager.getInstance().run(object : Task.Backgroundable(project, "AI 文件清理：自动清理", false) {
                    override fun run(indicator: ProgressIndicator) {
                        val logs = svc.applyAll(auto, indicator)
                        ApplicationManager.getApplication().invokeLater {
                            info(project, "已自动清理 ${logs.size} 个文件", logs.joinToString("\n"))
                        }
                    }
                })
            }
        }

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("AI File Janitor")
            .createNotification(
                "导入检测：发现 ${actionable.size} 个可处理文件",
                "AI 文件清理在新增文件中识别出临时/无用/配置文件。",
                NotificationType.INFORMATION,
            )
        notification.addAction(NotificationAction.createSimple("查看并清理") {
            notification.expire()
            val dialog = ScanResultDialog(project, all)
            if (dialog.showAndGet()) {
                val confirmed = dialog.confirmedResults()
                if (confirmed.isNotEmpty()) {
                    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "AI 文件清理：执行中", true) {
                        override fun run(indicator: ProgressIndicator) {
                            val logs = FileJanitorService.getInstance(project).applyAll(confirmed, indicator)
                            ApplicationManager.getApplication().invokeLater {
                                info(project, "AI 文件清理完成", logs.joinToString("\n"))
                            }
                        }
                    })
                }
            }
        })
        notification.notify(project)
    }

    private fun info(project: com.intellij.openapi.project.Project, title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("AI File Janitor")
            .createNotification(title, content, NotificationType.INFORMATION)
            .notify(project)
    }

    private fun inSkippedDir(file: VirtualFile): Boolean {
        var p: VirtualFile? = file.parent
        while (p != null) {
            if (p.name in skipDirNames) return true
            p = p.parent
        }
        return false
    }
}
