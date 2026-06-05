package com.lingce.cleanguard.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.util.concurrent.ConcurrentHashMap

class CleanGuardToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = CleanGuardPanel(project)
        panels[project.locationHash] = panel
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    companion object {
        private val panels = ConcurrentHashMap<String, CleanGuardPanel>()

        fun refresh(project: Project) {
            panels[project.locationHash]?.refreshTable()
        }

        fun getPanel(project: Project): CleanGuardPanel? =
            panels[project.locationHash]
    }
}
