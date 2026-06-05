package com.lingce.cleankeeper.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.lingce.cleankeeper.service.CleanupService
import com.lingce.cleankeeper.settings.CleanKeeperConfigurable
import com.lingce.cleankeeper.settings.CleanKeeperSettings

abstract class FileActionBase : AnAction() {

    protected fun getVirtualFiles(e: AnActionEvent) =
        e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.filter { !it.isDirectory } ?: emptyList()

    protected fun getProject(e: AnActionEvent): Project? = e.project
}

class ScanSelectedFileAction : FileActionBase() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = getProject(e) ?: return
        val files = getVirtualFiles(e)
        if (files.isEmpty()) return

        val service = CleanupService.getInstance(project)
        for (file in files) {
            val result = service.scanAndHandle(file, manual = true)
            Messages.showInfoMessage(
                project,
                "${file.name}\n分类: ${result.category.displayName}\n${result.reason}",
                "扫描结果",
            )
        }
    }
}

class QuarantineFileAction : FileActionBase() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = getProject(e) ?: return
        val service = CleanupService.getInstance(project)
        for (file in getVirtualFiles(e)) {
            service.quarantine(file)
        }
    }
}

class DeleteFileAction : FileActionBase() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = getProject(e) ?: return
        val files = getVirtualFiles(e)
        if (files.isEmpty()) return

        val names = files.joinToString("\n") { it.name }
        val ok = Messages.showYesNoDialog(
            project,
            "确认删除以下文件？\n$names",
            "删除确认",
            Messages.getWarningIcon(),
        )
        if (ok != Messages.YES) return

        val service = CleanupService.getInstance(project)
        for (file in files) {
            service.delete(file)
        }
    }
}

class AddToIgnoreAction : FileActionBase() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = getProject(e) ?: return
        val service = CleanupService.getInstance(project)
        for (file in getVirtualFiles(e)) {
            service.addToIgnore(file)
        }
    }
}

class ScanProjectAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (!CleanKeeperSettings.getInstance().enabled) {
            Messages.showWarningDialog(project, "请先在设置中启用 AI File CleanKeeper", "插件未启用")
            return
        }
        val results = CleanupService.getInstance(project).scanProject()
        Messages.showInfoMessage(
            project,
            "扫描完成，发现 ${results.size} 个需关注文件。请查看底部 AI CleanKeeper 工具窗口。",
            "项目扫描",
        )
    }
}

class OpenSettingsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        ShowSettingsUtil.getInstance().showSettingsDialog(e.project, CleanKeeperConfigurable::class.java)
    }
}
