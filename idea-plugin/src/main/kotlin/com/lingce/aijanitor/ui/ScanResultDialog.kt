package com.lingce.aijanitor.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.JBTable
import com.lingce.aijanitor.classify.ClassificationResult
import com.lingce.aijanitor.classify.RecommendedAction
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.DefaultCellEditor
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Review dialog: shows every classified file, lets the user toggle which ones
 * to act on and override the recommended action, then returns the final list.
 */
class ScanResultDialog(
    private val project: Project,
    results: List<ClassificationResult>,
) : DialogWrapper(project) {

    private val model = ScanResultTableModel(results.toMutableList(), project.basePath)
    private val table = JBTable(model)

    init {
        title = "AI 文件清理 · 扫描结果 (${results.size} 个文件)"
        setOKButtonText("执行选中操作")
        setCancelButtonText("取消")
        configureTable()
        init()
    }

    private fun configureTable() {
        table.setShowGrid(true)
        table.rowHeight = 24
        table.columnModel.getColumn(0).maxWidth = 50
        table.columnModel.getColumn(2).preferredWidth = 110
        table.columnModel.getColumn(3).preferredWidth = 150
        table.columnModel.getColumn(4).maxWidth = 70
        table.columnModel.getColumn(5).maxWidth = 90
        table.columnModel.getColumn(1).preferredWidth = 360
        table.columnModel.getColumn(6).preferredWidth = 320

        val combo = JComboBox(RecommendedAction.values().map { it.display }.toTypedArray())
        table.columnModel.getColumn(3).cellEditor = DefaultCellEditor(combo)
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        val actionable = model.results.count { it.isActionable }
        panel.add(
            JBLabel("共扫描 $totalCount 个文件，其中 $actionable 个建议处理。可调整每行的「建议操作」，取消勾选则跳过。"),
            BorderLayout.NORTH,
        )

        val decorated = ToolbarDecorator.createDecorator(table)
            .setAddActionName("全选")
            .setAddAction { model.setAllSelected(true) }
            .setRemoveActionName("全不选")
            .setRemoveAction { model.setAllSelected(false) }
            .disableUpDownActions()
            .createPanel()

        panel.add(decorated, BorderLayout.CENTER)
        panel.preferredSize = Dimension(1000, 560)
        return panel
    }

    private val totalCount get() = model.rowCount

    /** The files the user confirmed, with their (possibly overridden) actions. */
    fun confirmedResults(): List<ClassificationResult> = model.selectedActionable()
}
