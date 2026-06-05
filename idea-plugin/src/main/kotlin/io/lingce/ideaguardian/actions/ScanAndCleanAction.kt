package io.lingce.ideaguardian.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import io.lingce.ideaguardian.actions.runner.ScanAndCleanRunner

class ScanAndCleanAction : AnAction(), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT) ?: return
        ScanAndCleanRunner.run(project)
    }
}
