package com.lingce.cleanguard.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.lingce.cleanguard.service.ImportWatchService
import com.lingce.cleanguard.service.ScanService
import com.lingce.cleanguard.ui.CleanGuardToolWindowFactory

abstract class FileActionBase : AnAction() {

    protected fun getVirtualFile(e: AnActionEvent) =
        e.getData(CommonDataKeys.VIRTUAL_FILE)

    protected fun getProject(e: AnActionEvent): Project? =
        e.getData(CommonDataKeys.PROJECT)
}

class ScanCurrentFileAction : FileActionBase() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = getProject(e) ?: return
        val file = getVirtualFile(e) ?: return
        val scanService = ScanService.forProject(project)
        val classified = scanService.classifyFile(file)
        scanService.results.removeIf { it.path == classified.path }
        scanService.results.add(classified)
        CleanGuardToolWindowFactory.refresh(project)
        com.intellij.openapi.ui.Messages.showInfoMessage(
            project,
            "${classified.path}\n类别: ${classified.category.displayName}\n${classified.reason}",
            "Clean Guard 扫描结果",
        )
    }
}

class QuarantineCurrentFileAction : FileActionBase() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = getProject(e) ?: return
        val file = getVirtualFile(e) ?: return
        val classified = ScanService.forProject(project).classifyFile(file)
        ImportWatchService.getOrStart(project).processManual(
            classified,
            ImportWatchService.ManualAction.QUARANTINE,
        )
    }
}

class DeleteCurrentFileAction : FileActionBase() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = getProject(e) ?: return
        val file = getVirtualFile(e) ?: return
        val classified = ScanService.forProject(project).classifyFile(file)
        ImportWatchService.getOrStart(project).processManual(
            classified,
            ImportWatchService.ManualAction.DELETE,
        )
    }
}

class ScanProjectAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ScanService.forProject(project).scanProject { _ ->
            CleanGuardToolWindowFactory.refresh(project)
        }
        com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
            .getToolWindow("Clean Guard")
            ?.activate(null)
    }
}
