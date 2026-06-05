package com.lingce.aijanitor.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.lingce.aijanitor.service.FileOpsService
import com.lingce.aijanitor.util.Notifier

/** One-click: permanently delete the selected file(s) after a confirmation. */
class DeleteFilesAction : AnAction() {

    override fun update(e: AnActionEvent) {
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        e.presentation.isEnabledAndVisible = e.project != null && !files.isNullOrEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.filter { it.isValid } ?: return
        if (files.isEmpty()) return

        val names = files.joinToString("\n") { it.name }
        val choice = Messages.showYesNoDialog(
            project,
            "确认永久删除以下 ${files.size} 个文件/目录？此操作不可恢复。\n\n$names",
            "AI 文件清理 · 删除确认",
            "删除",
            "取消",
            Messages.getWarningIcon(),
        )
        if (choice != Messages.YES) return

        val ops = FileOpsService(project)
        val logs = files.map { file ->
            ops.delete(file).fold(
                onSuccess = { "已删除 ${file.name}" },
                onFailure = { "删除失败 ${file.name}: ${it.message}" },
            )
        }
        Notifier.info(project, "AI 文件清理 · 删除完成", logs.joinToString("\n"))
    }
}
