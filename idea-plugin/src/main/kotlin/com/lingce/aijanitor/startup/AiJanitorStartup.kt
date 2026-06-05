package com.lingce.aijanitor.startup

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import com.lingce.aijanitor.core.JanitorController
import com.lingce.aijanitor.settings.AiJanitorSettings

/** Offers to scan the project when it is opened ("我导入之后"). */
class AiJanitorStartup : ProjectActivity {

    override suspend fun execute(project: Project) {
        if (!AiJanitorSettings.getInstance().state.promptOnOpen) return

        NotificationGroupManager.getInstance()
            .getNotificationGroup("AI File Janitor")
            .createNotification(
                "AI 文件清道夫",
                "是否扫描该项目，识别 AI 生成/临时/配置/可疑文件并清理？",
                NotificationType.INFORMATION,
            )
            .addAction(NotificationAction.createSimple("立即扫描") {
                ToolWindowManager.getInstance(project).getToolWindow("AI File Janitor")?.show()
                JanitorController.getInstance(project).scan()
            })
            .addAction(NotificationAction.createSimple("打开设置") {
                com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                    .showSettingsDialog(project, com.lingce.aijanitor.settings.AiJanitorConfigurable::class.java)
            })
            .notify(project)
    }
}
