package com.lingce.aijanitor.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class AiJanitorToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = AiJanitorPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        content.setDisposer(Disposable { panel.dispose() })
        Disposer.register(toolWindow.disposable, content)
        toolWindow.contentManager.addContent(content)
    }
}
