package com.lingce.aicleaner.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.lingce.aicleaner.core.CleanupService
import com.lingce.aicleaner.core.Notifier

private fun selectedFiles(e: AnActionEvent): List<VirtualFile> =
    (e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.toList()
        ?: e.getData(CommonDataKeys.VIRTUAL_FILE)?.let { listOf(it) }
        ?: emptyList())
        .filter { it.isValid }

private fun runOnFiles(
    project: Project,
    files: List<VirtualFile>,
    title: String,
    op: (CleanupService, VirtualFile) -> Boolean,
) {
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, true) {
        override fun run(indicator: ProgressIndicator) {
            val cleanup = CleanupService(project)
            var ok = 0
            var fail = 0
            files.forEachIndexed { i, f ->
                indicator.checkCanceled()
                indicator.fraction = i.toDouble() / files.size
                indicator.text2 = f.name
                var r = false
                ApplicationManager.getApplication().invokeAndWait { r = op(cleanup, f) }
                if (r) ok++ else fail++
            }
            Notifier.info(project, title, "成功 $ok 项，失败 $fail 项。")
        }
    })
}

/** 一键转存到隔离目录。 */
class QuarantineFileAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val files = selectedFiles(e).ifEmpty { return }
        runOnFiles(project, files, "AI 清理：转存隔离") { c, f -> c.quarantine(f) }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && selectedFiles(e).isNotEmpty()
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

/** 一键删除（带二次确认）。 */
class DeleteSuspiciousFileAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val files = selectedFiles(e).ifEmpty { return }
        val confirm = Messages.showYesNoDialog(
            project,
            "确定要删除选中的 ${files.size} 个文件吗？此操作不可撤销。",
            "AI 清理：删除文件",
            Messages.getWarningIcon(),
        )
        if (confirm != Messages.YES) return
        runOnFiles(project, files, "AI 清理：删除") { c, f -> c.forceDelete(f) }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && selectedFiles(e).isNotEmpty()
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

/** 加入 .gitignore 并（目录时）标记 excluded。 */
class IgnoreExcludeFileAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val files = selectedFiles(e).ifEmpty { return }
        runOnFiles(project, files, "AI 清理：加入忽略/排除") { c, f -> c.ignoreAndExclude(f) }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && selectedFiles(e).isNotEmpty()
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
