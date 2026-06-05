package com.aifileguard.toolwindow

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Keeps a reference to the live tool-window panel so other components
 * (actions, startup activity, file watcher) can drive it.
 */
@Service(Service.Level.PROJECT)
class AiGuardPanelHolder {
    @Volatile
    var panel: AiGuardPanel? = null

    companion object {
        fun getInstance(project: Project): AiGuardPanelHolder = project.service()
    }
}
