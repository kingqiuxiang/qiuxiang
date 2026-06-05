package com.lingce.aicleaner.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.lingce.aicleaner.core.ProjectPaths
import com.lingce.aicleaner.model.CleanAction
import com.lingce.aicleaner.model.ClassificationResult
import javax.swing.table.AbstractTableModel

/**
 * 扫描结果表格模型。
 * 列：[处理?] [相对路径] [分类] [操作] [理由]
 */
class ScanResultTableModel(
    private val project: Project,
    val rows: MutableList<Row>,
) : AbstractTableModel() {

    data class Row(
        val result: ClassificationResult,
        var selected: Boolean,
        var action: CleanAction,
    )

    private val columns = arrayOf("处理", "文件", "分类", "操作", "依据")

    override fun getRowCount(): Int = rows.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column]

    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
        0 -> java.lang.Boolean::class.java
        else -> String::class.java
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean =
        columnIndex == 0 || columnIndex == 3

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = rows[rowIndex]
        return when (columnIndex) {
            0 -> row.selected
            1 -> relPath(row)
            2 -> row.result.category.displayName
            3 -> actionLabel(row.action)
            4 -> row.result.reason + if (row.result.byAi) " [AI]" else ""
            else -> ""
        }
    }

    override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
        val row = rows[rowIndex]
        when (columnIndex) {
            0 -> row.selected = value as? Boolean ?: false
            3 -> {
                val label = value?.toString() ?: return
                CleanAction.entries.firstOrNull { actionLabel(it) == label }?.let { row.action = it }
            }
        }
        fireTableRowsUpdated(rowIndex, rowIndex)
    }

    fun selectAll(selected: Boolean) {
        rows.forEach { it.selected = selected }
        fireTableDataChanged()
    }

    private fun relPath(row: Row): String {
        val base = ProjectPaths.baseDir(project)
        return if (base != null) VfsUtilCore.getRelativePath(row.result.file, base) ?: row.result.file.path
        else row.result.file.path
    }

    companion object {
        fun actionLabel(a: CleanAction): String = when (a) {
            CleanAction.ASK -> CleanAction.QUARANTINE.displayName // 可疑文件默认转存
            else -> a.displayName
        }
    }
}
