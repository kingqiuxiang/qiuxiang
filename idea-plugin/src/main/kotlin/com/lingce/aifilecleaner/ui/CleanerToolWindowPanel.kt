package com.lingce.aifilecleaner.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.panels.HorizontalLayout
import com.lingce.aifilecleaner.model.ScanResult
import com.lingce.aifilecleaner.service.FileCleanupService
import java.awt.BorderLayout
import java.nio.file.Path
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JPanel

class CleanerToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val service = project.service<FileCleanupService>()
    private val summaryArea = JBTextArea()
    private val listModel = DefaultListModel<String>()
    private val suspiciousList = JBList(listModel)
    private val suspiciousPaths = mutableListOf<Path>()

    init {
        val buttonRow = JPanel(HorizontalLayout(8))
        val scanButton = JButton("立即扫描")
        val archiveButton = JButton("一键转存可疑文件")
        val deleteButton = JButton("一键删除可疑文件")
        val settingsButton = JButton("打开设置")

        scanButton.addActionListener {
            summaryArea.text = "扫描中，请稍候..."
            service.runScan(trigger = "tool-window") { result ->
                updateUi(result)
            }
        }
        archiveButton.addActionListener {
            service.archiveSuspiciousFiles()
            service.runScan(trigger = "post-archive") { result ->
                updateUi(result)
            }
        }
        deleteButton.addActionListener {
            service.deleteSuspiciousFiles()
            service.runScan(trigger = "post-delete") { result ->
                updateUi(result)
            }
        }
        settingsButton.addActionListener {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, "AI File Cleaner")
        }

        buttonRow.add(scanButton)
        buttonRow.add(archiveButton)
        buttonRow.add(deleteButton)
        buttonRow.add(settingsButton)

        summaryArea.isEditable = false
        summaryArea.lineWrap = true
        summaryArea.wrapStyleWord = true
        summaryArea.text = "还未执行扫描。"

        add(buttonRow, BorderLayout.NORTH)
        add(JBScrollPane(summaryArea), BorderLayout.CENTER)
        add(JBScrollPane(suspiciousList), BorderLayout.SOUTH)

        updateUi(service.lastResult())
    }

    private fun updateUi(result: ScanResult) {
        ApplicationManager.getApplication().invokeLater {
            summaryArea.text = buildSummary(result)
            suspiciousPaths.clear()
            suspiciousPaths += result.suspiciousFiles
            listModel.removeAllElements()
            if (result.suspiciousFiles.isEmpty()) {
                listModel.addElement("无可疑文件")
            } else {
                result.suspiciousFiles.forEach { listModel.addElement(it.toString()) }
            }
        }
    }

    private fun buildSummary(result: ScanResult): String {
        val base = StringBuilder()
        base.appendLine("扫描文件数: ${result.scannedFiles}")
        base.appendLine("已删除 tmp: ${result.deletedTmpFiles}")
        base.appendLine("已删除 AI 垃圾: ${result.deletedAiJunkFiles}")
        base.appendLine("已归档配置/AI 配置: ${result.movedConfigFiles}")
        base.appendLine("可疑文件数: ${result.suspiciousFiles.size}")
        if (result.errors.isNotEmpty()) {
            base.appendLine()
            base.appendLine("错误（最多显示 10 条）:")
            result.errors.take(10).forEach { base.appendLine("- $it") }
        }
        return base.toString()
    }
}
