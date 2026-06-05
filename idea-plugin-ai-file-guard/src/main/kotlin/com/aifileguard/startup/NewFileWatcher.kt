package com.aifileguard.startup

import com.aifileguard.classify.FileGuardScanner
import com.aifileguard.model.FileVerdict
import com.aifileguard.toolwindow.AiGuardPanelHolder
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindowManager
import java.nio.file.Path

/**
 * Watches for newly created / imported files and flags junk in near real time,
 * so it can be cleaned promptly after an import.
 */
object NewFileWatcher {

    private const val MAX_EVENTS = 300

    fun register(project: Project) {
        val connection = project.messageBus.connect()
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (project.isDisposed) return
                val created = events.asSequence()
                    .filter { it is VFileCreateEvent || it is VFileCopyEvent }
                    .mapNotNull { it.file?.path ?: it.path }
                    .take(MAX_EVENTS)
                    .toList()
                if (created.isEmpty()) return

                ApplicationManager.getApplication().executeOnPooledThread {
                    if (project.isDisposed) return@executeOnPooledThread
                    val scanner = project.getService(FileGuardScanner::class.java) ?: return@executeOnPooledThread
                    val flagged = created.mapNotNull { pathStr ->
                        runCatching { scanner.classifySingle(Path.of(pathStr), withAi = false) }.getOrNull()
                    }
                    if (flagged.isNotEmpty()) {
                        ApplicationManager.getApplication().invokeLater { notify(project, flagged) }
                    }
                }
            }
        })
    }

    private fun notify(project: Project, flagged: List<FileVerdict>) {
        if (project.isDisposed) return
        val names = flagged.take(3).joinToString(", ") { it.relativePath }
        val more = if (flagged.size > 3) " (+${flagged.size - 3} more)" else ""
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("AI File Guard")
            .createNotification(
                "AI File Guard — new files detected",
                "${flagged.size} newly added file(s) look like junk/config: $names$more",
                NotificationType.INFORMATION,
            )
        notification.addAction(NotificationAction.createSimple("Review & clean") {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("AI File Guard")
            toolWindow?.activate {
                AiGuardPanelHolder.getInstance(project).panel?.setResults(flagged)
            } ?: AiGuardPanelHolder.getInstance(project).panel?.setResults(flagged)
            notification.expire()
        })
        notification.notify(project)
    }
}
