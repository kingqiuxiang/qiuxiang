package com.lingce.aicleaner.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.lingce.aicleaner.core.CleanupService
import com.lingce.aicleaner.core.Notifier
import com.lingce.aicleaner.model.CleanAction
import com.lingce.aicleaner.model.ClassificationResult
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.DefaultCellEditor
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * 扫描结果对话框：勾选文件、选择/确认操作，一键执行。
 */
class ScanResultDialog(
    private val project: Project,
    results: List<ClassificationResult>,
) : DialogWrapper(project) {

    private val tableModel = ScanResultTableModel(
        project,
        results.map {
            ScanResultTableModel.Row(
                result = it,
                selected = it.recommendedAction != CleanAction.KEEP,
                action = it.recommendedAction,
            )
        }.toMutableList(),
    )

    private val table = JBTable(tableModel)

    init {
        title = "AI 文件清理 — 扫描结果（${results.size} 项）"
        setOKButtonText("执行所选操作")
        setupTable()
        init()
    }

    private fun setupTable() {
        table.setShowGrid(true)
        table.rowHeight = 24
        table.columnModel.getColumn(0).preferredWidth = 50
        table.columnModel.getColumn(1).preferredWidth = 360
        table.columnModel.getColumn(2).preferredWidth = 130
        table.columnModel.getColumn(3).preferredWidth = 120
        table.columnModel.getColumn(4).preferredWidth = 300

        val combo = JComboBox(
            arrayOf(
                CleanAction.DELETE.displayName,
                CleanAction.IGNORE_EXCLUDE.displayName,
                CleanAction.QUARANTINE.displayName,
                CleanAction.KEEP.displayName,
            ),
        )
        table.columnModel.getColumn(3).cellEditor = DefaultCellEditor(combo)
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(1000, 520)

        val top = JPanel(FlowLayout(FlowLayout.LEFT))
        top.add(JBLabel("勾选要处理的文件，可在「操作」列调整动作；可疑文件默认转存隔离。"))
        panel.add(top, BorderLayout.NORTH)

        panel.add(JBScrollPane(table), BorderLayout.CENTER)

        val bottom = JPanel(FlowLayout(FlowLayout.LEFT))
        bottom.add(JButton("全选").apply { addActionListener { tableModel.selectAll(true) } })
        bottom.add(JButton("全不选").apply { addActionListener { tableModel.selectAll(false) } })
        bottom.add(JButton("可疑→全部转存隔离").apply {
            addActionListener { setActionForSuspicious(CleanAction.QUARANTINE) }
        })
        bottom.add(JButton("可疑→全部删除").apply {
            addActionListener { setActionForSuspicious(CleanAction.DELETE) }
        })
        panel.add(bottom, BorderLayout.SOUTH)

        return panel
    }

    private fun setActionForSuspicious(action: CleanAction) {
        tableModel.rows.forEach {
            if (it.result.category == com.lingce.aicleaner.model.FileCategory.SUSPICIOUS) {
                it.action = action
                it.selected = true
            }
        }
        tableModel.fireTableDataChanged()
    }

    override fun doOKAction() {
        if (table.isEditing) table.cellEditor?.stopCellEditing()
        val toProcess = tableModel.rows.filter { it.selected && it.action != CleanAction.KEEP }
        if (toProcess.isEmpty()) {
            super.doOKAction()
            return
        }
        super.doOKAction()
        runActions(toProcess)
    }

    private fun runActions(rows: List<ScanResultTableModel.Row>) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "执行清理…", true) {
            override fun run(indicator: ProgressIndicator) {
                val cleanup = CleanupService(project)
                var ok = 0
                var fail = 0
                rows.forEachIndexed { i, row ->
                    indicator.checkCanceled()
                    indicator.fraction = i.toDouble() / rows.size
                    indicator.text2 = row.result.file.name
                    val success = ApplicationManager.getApplication().let {
                        var r = false
                        it.invokeAndWait {
                            r = when (row.action) {
                                CleanAction.DELETE -> cleanup.delete(row.result.file)
                                CleanAction.QUARANTINE -> cleanup.quarantine(row.result.file)
                                CleanAction.IGNORE_EXCLUDE -> cleanup.ignoreAndExclude(row.result.file)
                                else -> true
                            }
                        }
                        r
                    }
                    if (success) ok++ else fail++
                }
                Notifier.info(project, "AI 文件清理完成", "成功 $ok 项，失败 $fail 项。")
            }
        })
    }
}
