package com.aifileguard.startup

import com.aifileguard.classify.FileGuardScanner
import com.aifileguard.settings.AiGuardSettings
import com.aifileguard.toolwindow.AiGuardPanelHolder
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Runs automatically when a project is opened/imported. Performs a fast,
 * offline scan and, if junk is detected, surfaces a notification that lets the
 * user open the AI File Guard tool window to review and clean.
 */
class ImportScanActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val settings = AiGuardSettings.getInstance().state
        if (settings.watchNewFiles) {
            NewFileWatcher.register(project)
        }
        if (!settings.scanOnOpen) return

        val scanner = project.getService(FileGuardScanner::class.java) ?: return
        val results = ReadAction.compute<List<com.aifileguard.model.FileVerdict>, RuntimeException> {
            scanner.scan(indicator = null, allowAi = false)
        }
        if (results.isEmpty()) return

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("AI File Guard")
            .createNotification(
                "AI File Guard",
                "Found ${results.size} AI-generated / temporary / suspicious file(s). Review to keep your workspace clean.",
                NotificationType.INFORMATION,
            )
        notification.addAction(NotificationAction.createSimple("Open AI File Guard") {
            openAndShow(project, results)
            notification.expire()
        })
        notification.notify(project)
    }

    private fun openAndShow(project: Project, results: List<com.aifileguard.model.FileVerdict>) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("AI File Guard")
        toolWindow?.activate {
            val panel = AiGuardPanelHolder.getInstance(project).panel
            // Re-run with AI enabled for an accurate verdict, falling back to the quick results.
            if (panel != null) panel.runScan() else Unit
        } ?: AiGuardPanelHolder.getInstance(project).panel?.setResults(results)
    }
}
