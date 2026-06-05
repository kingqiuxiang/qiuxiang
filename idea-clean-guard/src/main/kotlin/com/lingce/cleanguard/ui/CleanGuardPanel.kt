package com.lingce.cleanguard.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.lingce.cleanguard.model.ClassifiedFile
import com.lingce.cleanguard.model.FileCategory
import com.lingce.cleanguard.service.ImportWatchService
import com.lingce.cleanguard.service.ScanService
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.table.AbstractTableModel

class CleanGuardPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val scanService = ScanService.forProject(project)
    private val watchService = ImportWatchService.getOrStart(project)

    private val tableModel = FileTableModel()
    private val table = JBTable(tableModel).apply {
        setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION)
        autoCreateRowSorter = true
    }

    private val scanButton = JButton("扫描项目").apply {
        addActionListener { scanProject() }
    }
    private val quarantineButton = JButton("转存隔离").apply {
        addActionListener { act(ImportWatchService.ManualAction.QUARANTINE) }
    }
    private val deleteButton = JButton("一键删除").apply {
        addActionListener { act(ImportWatchService.ManualAction.DELETE) }
    }
    private val excludeButton = JButton("加入 Ignore/Exclude").apply {
        addActionListener { act(ImportWatchService.ManualAction.EXCLUDE) }
    }

    init {
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(scanButton)
            add(quarantineButton)
            add(deleteButton)
            add(excludeButton)
        }
        add(toolbar, BorderLayout.NORTH)
        add(JBScrollPane(table), BorderLayout.CENTER)
    }

    fun refreshTable() {
        tableModel.setRows(scanService.results.toList())
    }

    private fun scanProject() {
        scanButton.isEnabled = false
        scanService.scanProject { found ->
            tableModel.setRows(found)
            scanButton.isEnabled = true
            Messages.showInfoMessage(
                project,
                "扫描完成，发现 ${found.size} 个需关注文件",
                "Clean Guard",
            )
        }
    }

    private fun act(action: ImportWatchService.ManualAction) {
        val row = table.selectedRow
        if (row < 0) {
            Messages.showWarningDialog(project, "请先选择一行", "Clean Guard")
            return
        }
        val modelRow = table.convertRowIndexToModel(row)
        val item = tableModel.getAt(modelRow)

        val confirm = when (action) {
            ImportWatchService.ManualAction.DELETE ->
                Messages.showYesNoDialog(project, "确认删除 ${item.path}？", "Clean Guard", Messages.getWarningIcon())
            ImportWatchService.ManualAction.QUARANTINE ->
                Messages.showYesNoDialog(project, "确认转存 ${item.path} 到隔离区？", "Clean Guard", Messages.getQuestionIcon())
            ImportWatchService.ManualAction.EXCLUDE ->
                Messages.showYesNoDialog(project, "确认将 ${item.path} 加入 ignore/exclude？", "Clean Guard", Messages.getQuestionIcon())
        }
        if (confirm != Messages.YES) return

        watchService.processManual(item, action)
        refreshTable()
    }

    private class FileTableModel : AbstractTableModel() {
        private val columns = arrayOf("路径", "类别", "原因", "置信度")
        private var rows = emptyList<ClassifiedFile>()

        fun setRows(data: List<ClassifiedFile>) {
            rows = data.sortedByDescending { it.confidence }
            fireTableDataChanged()
        }

        fun getAt(index: Int): ClassifiedFile = rows[index]

        override fun getRowCount(): Int = rows.size
        override fun getColumnCount(): Int = columns.size
        override fun getColumnName(column: Int): String = columns[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any = when (columnIndex) {
            0 -> rows[rowIndex].path
            1 -> rows[rowIndex].category.displayName
            2 -> rows[rowIndex].reason
            3 -> "%.0f%%".format(rows[rowIndex].confidence * 100)
            else -> ""
        }
    }
}
