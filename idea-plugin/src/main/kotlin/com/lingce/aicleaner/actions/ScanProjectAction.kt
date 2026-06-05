package com.lingce.aicleaner.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile
import com.lingce.aicleaner.core.Notifier
import com.lingce.aicleaner.core.ProjectPaths
import com.lingce.aicleaner.core.ScanService
import com.lingce.aicleaner.ui.ScanResultDialog

/**
 * 扫描项目（或选中目录），对文件分类并弹出结果对话框。
 */
class ScanProjectAction : DumbAwareAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val roots = resolveRoots(e, project)
        if (roots.isEmpty()) {
            Notifier.warn(project, "AI 文件清理", "未找到可扫描的目录。")
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "AI 文件清理：扫描中…", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val results = ScanService(project).scan(roots, indicator)
                ApplicationManager.getApplication().invokeLater {
                    if (results.isEmpty()) {
                        Notifier.info(project, "AI 文件清理", "未发现需要处理的文件，工作区很干净。")
                    } else {
                        ScanResultDialog(project, results).show()
                    }
                }
            }
        })
    }

    private fun resolveRoots(e: AnActionEvent, project: Project): List<VirtualFile> {
        val selected = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
            ?: e.getData(PlatformDataKeys.VIRTUAL_FILE)?.let { arrayOf(it) }
        val dirs = selected?.filter { it.isValid }?.map { if (it.isDirectory) it else it.parent }?.distinct()
        if (!dirs.isNullOrEmpty()) return dirs
        return ProjectPaths.baseDir(project)?.let { listOf(it) } ?: emptyList()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
}
