package com.lingce.cleankeeper.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class CleanKeeperToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = CleanKeeperPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
        panels[project.locationHash] = panel
    }

    companion object {
        private val panels = mutableMapOf<String, CleanKeeperPanel>()

        fun refresh(project: Project) {
            panels[project.locationHash]?.refresh()
        }
    }
}
