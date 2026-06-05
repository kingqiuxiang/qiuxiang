package com.lingce.aijanitor.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.lingce.aijanitor.service.FileOpsService
import com.lingce.aijanitor.settings.AiJanitorSettings
import com.lingce.aijanitor.util.Notifier

/** One-click: move the selected file(s) into the configured quarantine directory. */
class QuarantineFilesAction : AnAction() {

    override fun update(e: AnActionEvent) {
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        e.presentation.isEnabledAndVisible = e.project != null && !files.isNullOrEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.toList() ?: return
        val ops = FileOpsService(project)
        val logs = files.filter { it.isValid }.map { file ->
            ops.quarantine(file).fold(
                onSuccess = { "✔ ${file.name} -> $it" },
                onFailure = { "x ${file.name}: ${it.message}" },
            )
        }
        Notifier.info(
            project,
            "已转存到隔离目录 (${AiJanitorSettings.getInstance().quarantineDir})",
            logs.joinToString("\n"),
        )
    }
}
