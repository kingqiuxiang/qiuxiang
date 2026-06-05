package com.aifileguard.toolwindow

import com.aifileguard.model.FileVerdict
import javax.swing.table.AbstractTableModel

class VerdictTableModel : AbstractTableModel() {

    private val rows = ArrayList<FileVerdict>()

    private val columns = listOf("File", "Category", "Suggested action", "Confidence", "Size", "Source", "Reason")

    fun setRows(newRows: List<FileVerdict>) {
        rows.clear()
        rows.addAll(newRows)
        fireTableDataChanged()
    }

    fun getRow(index: Int): FileVerdict = rows[index]

    fun rows(): List<FileVerdict> = rows.toList()

    fun removeRows(toRemove: Collection<FileVerdict>) {
        if (rows.removeAll(toRemove.toSet())) {
            fireTableDataChanged()
        }
    }

    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int = columns.size

    override fun getColumnName(column: Int): String = columns[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val v = rows[rowIndex]
        return when (columnIndex) {
            0 -> v.relativePath
            1 -> v.category.display
            2 -> v.action.display
            3 -> String.format("%.0f%%", v.confidence * 100)
            4 -> formatSize(v.sizeBytes)
            5 -> if (v.byAi) "AI" else "Rule"
            6 -> v.reason
            else -> ""
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
