package com.lingce.aijanitor.ui

import com.lingce.aijanitor.classify.ClassificationResult
import com.lingce.aijanitor.classify.RecommendedAction
import javax.swing.table.AbstractTableModel

class ScanResultTableModel(
    val results: MutableList<ClassificationResult>,
    private val projectBasePath: String?,
) : AbstractTableModel() {

    val selected = BooleanArray(results.size) { results[it].isActionable }

    private val columns = arrayOf("应用", "文件", "类别", "建议操作", "置信", "来源", "原因")

    override fun getRowCount(): Int = results.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column]

    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
        0 -> java.lang.Boolean::class.java
        else -> String::class.java
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean =
        columnIndex == 0 || columnIndex == 3

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val r = results[rowIndex]
        return when (columnIndex) {
            0 -> selected[rowIndex]
            1 -> relativePath(r)
            2 -> r.category.display
            3 -> r.action.display
            4 -> "%.0f%%".format(r.confidence * 100)
            5 -> r.source
            6 -> r.reason
            else -> ""
        }
    }

    override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
        when (columnIndex) {
            0 -> selected[rowIndex] = value as? Boolean ?: false
            3 -> {
                val action = RecommendedAction.values().firstOrNull { it.display == value }
                if (action != null) {
                    results[rowIndex] = results[rowIndex].copy(action = action)
                    selected[rowIndex] = action != RecommendedAction.KEEP
                    fireTableRowsUpdated(rowIndex, rowIndex)
                }
            }
        }
    }

    fun selectedActionable(): List<ClassificationResult> =
        results.indices.filter { selected[it] && results[it].action != RecommendedAction.KEEP }
            .map { results[it] }

    fun setAllSelected(value: Boolean) {
        for (i in selected.indices) {
            if (results[i].action != RecommendedAction.KEEP) selected[i] = value
        }
        fireTableDataChanged()
    }

    private fun relativePath(r: ClassificationResult): String {
        val base = projectBasePath?.trimEnd('/') ?: return r.file.path
        return if (r.file.path.startsWith(base)) r.file.path.removePrefix(base).trimStart('/')
        else r.file.path
    }
}
