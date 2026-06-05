package com.lingce.cleankeeper.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.JBSplitter
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.lingce.cleankeeper.service.CleanupService
import com.lingce.cleankeeper.service.TrackedFile
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

class CleanKeeperPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val cleanupService = CleanupService.getInstance(project)
    private val tableModel = TrackedFileTableModel()
    private val table = JBTable(tableModel)
    private val detailLabel = JBLabel("选择文件查看分类详情").apply {
        border = JBUI.Borders.empty(8)
    }

    init {
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        table.setShowGrid(false)
        table.rowHeight = 28
        table.columnModel.getColumn(0).cellRenderer = CategoryRenderer()
        table.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) updateDetail()
        }

        val decorated = ToolbarDecorator.createDecorator(table)
            .disableAddAction()
            .setRemoveAction {
                val row = table.selectedRow
                if (row >= 0) {
                    cleanupService.delete(tableModel.getAt(row).file)
                    refresh()
                }
            }
            .addExtraAction(ScanProjectToolbarAction(project))
            .addExtraAction(QuarantineToolbarAction(project) { selectedFile() })
            .addExtraAction(AddIgnoreToolbarAction(project) { selectedFile() })
            .createPanel()

        val splitter = JBSplitter(false, 0.75f).apply {
            firstComponent = decorated
            secondComponent = JBScrollPane(detailLabel)
        }

        add(splitter, BorderLayout.CENTER)
        refresh()
    }

    private fun selectedFile() = table.selectedRow.takeIf { it >= 0 }?.let { tableModel.getAt(it).file }

    fun refresh() {
        tableModel.setData(cleanupService.getTrackedFiles())
        if (table.rowCount > 0 && table.selectedRow < 0) {
            table.selectionModel.setSelectionInterval(0, 0)
        }
        updateDetail()
    }

    private fun updateDetail() {
        val row = table.selectedRow
        if (row < 0) {
            detailLabel.text = "选择文件查看分类详情"
            return
        }
        val tracked = tableModel.getAt(row)
        val c = tracked.classification
        detailLabel.text = buildString {
            append("<html><body style='font-family: sans-serif;'>")
            append("<b>${tracked.relativePath}</b><br><br>")
            append("<b>分类：</b>${c.category.displayName}<br>")
            append("<b>置信度：</b>${"%.0f".format(c.confidence * 100)}%<br>")
            append("<b>来源：</b>${c.source.name}<br>")
            append("<b>说明：</b>${c.reason}<br><br>")
            append("<i>工具栏：扫描项目 · 隔离转存 · 删除 · 加入 Ignore</i>")
            append("</body></html>")
        }
    }

    private class TrackedFileTableModel : AbstractTableModel() {
        private var data: List<TrackedFile> = emptyList()

        fun setData(items: List<TrackedFile>) {
            data = items
            fireTableDataChanged()
        }

        fun getAt(row: Int): TrackedFile = data[row]

        override fun getRowCount(): Int = data.size
        override fun getColumnCount(): Int = 3
        override fun getColumnName(column: Int): String = when (column) {
            0 -> "分类"
            1 -> "路径"
            2 -> "置信度"
            else -> ""
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any = when (columnIndex) {
            0 -> data[rowIndex].classification.category.displayName
            1 -> data[rowIndex].relativePath
            2 -> "${"%.0f".format(data[rowIndex].classification.confidence * 100)}%"
            else -> ""
        }
    }

    private class CategoryRenderer : ColoredTableCellRenderer() {
        override fun customizeCellRenderer(
            table: javax.swing.JTable,
            value: Any?,
            selected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ) {
            append(value?.toString() ?: "")
            when (value) {
                "临时文件", "AI 无用产物" -> foreground = java.awt.Color(200, 80, 80)
                "可疑文件" -> foreground = java.awt.Color(200, 140, 40)
                "项目配置", "AI 配置" -> foreground = java.awt.Color(80, 120, 200)
                else -> {}
            }
        }
    }
}

private class ScanProjectToolbarAction(private val project: Project) : AnAction("扫描项目", "扫描整个项目", com.intellij.icons.AllIcons.Actions.Find) {
    override fun actionPerformed(e: AnActionEvent) {
        CleanupService.getInstance(project).scanProject()
        CleanKeeperToolWindowFactory.refresh(project)
    }
}

private class QuarantineToolbarAction(
    private val project: Project,
    private val fileProvider: () -> com.intellij.openapi.vfs.VirtualFile?,
) : AnAction("隔离转存", "转存到隔离目录", com.intellij.icons.AllIcons.Actions.GC) {
    override fun actionPerformed(e: AnActionEvent) {
        fileProvider()?.let { CleanupService.getInstance(project).quarantine(it) }
        CleanKeeperToolWindowFactory.refresh(project)
    }
}

private class AddIgnoreToolbarAction(
    private val project: Project,
    private val fileProvider: () -> com.intellij.openapi.vfs.VirtualFile?,
) : AnAction("加入 Ignore", "加入 .gitignore", com.intellij.icons.AllIcons.Actions.Cancel) {
    override fun actionPerformed(e: AnActionEvent) {
        fileProvider()?.let { CleanupService.getInstance(project).addToIgnore(it) }
    }
}
