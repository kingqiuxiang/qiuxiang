package com.lingce.aifilecleaner.startup

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.lingce.aifilecleaner.service.FileCleanupService
import com.lingce.aifilecleaner.settings.CleanerSettingsState

class ProjectOpenScanner : StartupActivity.DumbAware {
    override fun runActivity(project: Project) {
        val settings = service<CleanerSettingsState>().state
        if (!settings.autoScanOnStartup) {
            return
        }
        project.service<FileCleanupService>().runScan(trigger = "startup")
    }
}
