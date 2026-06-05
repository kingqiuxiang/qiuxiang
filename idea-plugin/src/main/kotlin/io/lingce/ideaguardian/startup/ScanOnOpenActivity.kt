package io.lingce.ideaguardian.startup

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.Messages
import io.lingce.ideaguardian.actions.runner.ScanAndCleanRunner

class ScanOnOpenActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            val answer = Messages.showYesNoDialog(
                project,
                "已检测到项目导入完成，是否立即执行 AI 文件清理扫描？",
                "AI File Guardian",
                "立即扫描",
                "稍后再说",
                null
            )
            if (answer == Messages.YES) {
                ScanAndCleanRunner.run(project)
            }
        }
    }
}
