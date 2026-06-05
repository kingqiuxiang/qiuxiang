package com.lingce.cleanguard.service

import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.lingce.cleanguard.settings.CleanGuardSettings

object CleanGuardNotifier {

    private const val GROUP_ID = "Clean Guard"

    fun info(project: Project?, message: String) = notify(project, message, NotificationType.INFORMATION)

    fun warn(project: Project?, message: String) = notify(project, message, NotificationType.WARNING)

    private fun notify(project: Project?, message: String, type: NotificationType) {
        if (!CleanGuardSettings.getInstance().state.notifyOnAction) return
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup(GROUP_ID)
                .createNotification("Clean Guard", message, type)
                .notify(project)
        }
    }
}
