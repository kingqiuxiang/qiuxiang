package com.lingce.cleanguardian

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class ScanAndCleanProjectAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        project.getService(CleanGuardianProjectService::class.java).triggerManualScan()
    }
}
