package io.lingce.ideaguardian.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import io.lingce.ideaguardian.service.ProjectFileScanner
import io.lingce.ideaguardian.settings.AiFileGuardianSettingsService
import io.lingce.ideaguardian.ui.ScanResultDialog

class ScanAndCleanAction : AnAction(), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT) ?: return
        runScan(project)
    }

    private fun runScan(project: Project) {
        val settings = service<AiFileGuardianSettingsService>().state
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "AI File Guardian Scanning", true) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                indicator.isIndeterminate = false
                val scanner = ProjectFileScanner(settings)
                val result = scanner.scan(project, indicator)
                ApplicationManager.getApplication().invokeLater {
                    ScanResultDialog(project, result, settings).show()
                }
            }
        })
    }
}
