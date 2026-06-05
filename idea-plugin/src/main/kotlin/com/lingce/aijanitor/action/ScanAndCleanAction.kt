package com.lingce.aijanitor.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.lingce.aijanitor.classify.ClassificationResult
import com.lingce.aijanitor.classify.RecommendedAction
import com.lingce.aijanitor.service.FileJanitorService
import com.lingce.aijanitor.settings.AiJanitorSettings
import com.lingce.aijanitor.ui.ScanResultDialog
import com.lingce.aijanitor.util.Notifier

/**
 * Main entry point: scan the selected files (or the whole project), classify
 * them, let the user review, then clean up.
 */
class ScanAndCleanAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val roots = resolveRoots(e, project)
        if (roots.isEmpty()) {
            Notifier.warn(project, "AI 文件清理", "未找到可扫描的文件。")
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "AI 文件清理：扫描中", true) {
            override fun run(indicator: ProgressIndicator) {
                val service = FileJanitorService.getInstance(project)
                val results = service.scan(roots, indicator)
                ApplicationManager.getApplication().invokeLater {
                    handleResults(project, results)
                }
            }
        })
    }

    private fun handleResults(project: Project, results: List<ClassificationResult>) {
        val actionable = results.filter { it.isActionable }
        if (actionable.isEmpty()) {
            Notifier.info(project, "AI 文件清理", "扫描完成，未发现需要清理的文件。本地很干净 ✨")
            return
        }

        val settings = AiJanitorSettings.getInstance()
        val toApply: List<ClassificationResult> = if (settings.autoDeleteConfident) {
            // Auto-apply confident temp/useless deletions; review the rest.
            val auto = actionable.filter {
                it.action == RecommendedAction.DELETE && it.confidence >= 0.85
            }
            val review = results.filterNot { auto.contains(it) }
            applyInBackground(project, auto, silent = true)
            val dialogResults = reviewDialog(project, review) ?: return
            dialogResults
        } else {
            reviewDialog(project, results) ?: return
        }

        if (toApply.isEmpty()) {
            Notifier.info(project, "AI 文件清理", "没有选择任何操作。")
            return
        }
        applyInBackground(project, toApply, silent = false)
    }

    private fun reviewDialog(project: Project, results: List<ClassificationResult>): List<ClassificationResult>? {
        if (results.none { it.isActionable }) return emptyList()
        val dialog = ScanResultDialog(project, results)
        return if (dialog.showAndGet()) dialog.confirmedResults() else null
    }

    private fun applyInBackground(project: Project, results: List<ClassificationResult>, silent: Boolean) {
        if (results.isEmpty()) return
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "AI 文件清理：执行中", true) {
            override fun run(indicator: ProgressIndicator) {
                val logs = FileJanitorService.getInstance(project).applyAll(results, indicator)
                ApplicationManager.getApplication().invokeLater {
                    val summary = logs.joinToString("\n").ifBlank { "无操作" }
                    Notifier.info(project, "AI 文件清理完成", "处理 ${logs.size} 项：\n$summary")
                }
            }
        })
    }

    private fun resolveRoots(e: AnActionEvent, project: Project): List<VirtualFile> {
        val selected = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (selected != null && selected.isNotEmpty()) return selected.toList()
        val base = project.basePath ?: return emptyList()
        return LocalFileSystem.getInstance().findFileByPath(base)?.let { listOf(it) } ?: emptyList()
    }
}
