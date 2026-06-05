package com.aifileguard.action

import com.aifileguard.toolwindow.AiGuardPanelHolder
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.wm.ToolWindowManager

class ScanAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("AI File Guard")
        if (toolWindow == null) {
            AiGuardPanelHolder.getInstance(project).panel?.runScan()
            return
        }
        toolWindow.activate {
            AiGuardPanelHolder.getInstance(project).panel?.runScan()
        }
    }
}
