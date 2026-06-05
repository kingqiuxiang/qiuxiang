package com.lingce.cleaner.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.lingce.cleaner.services.FileCleanupService

class ScanAndCleanNowAction : AnAction(), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        project.service<FileCleanupService>().scanProjectAndApplyCleanup(trigger = "manual", showSummary = true)
    }
}
