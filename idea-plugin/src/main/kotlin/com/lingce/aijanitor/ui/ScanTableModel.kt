package com.lingce.aijanitor.ui

import com.lingce.aijanitor.model.CleanupAction
import com.lingce.aijanitor.model.ScanItem
import javax.swing.table.AbstractTableModel

class ScanTableModel : AbstractTableModel() {

    private val columns = arrayOf("✓", "文件", "类别", "操作", "原因")
    var rows: MutableList<ScanItem> = mutableListOf()
        private set

    fun setItems(items: List<ScanItem>) {
        rows = items.toMutableList()
        fireTableDataChanged()
    }

    fun itemAt(row: Int): ScanItem = rows[row]

    fun selectedItems(): List<ScanItem> = rows.filter { it.selected }

    fun setAllSelected(selected: Boolean) {
        rows.forEach { it.selected = selected }
        fireTableDataChanged()
    }

    override fun getRowCount(): Int = rows.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column]

    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
        0 -> java.lang.Boolean::class.java
        3 -> CleanupAction::class.java
        else -> String::class.java
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex == 0 || columnIndex == 3

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val item = rows[rowIndex]
        return when (columnIndex) {
            0 -> item.selected
            1 -> item.relativePath
            2 -> item.category.display
            3 -> item.action
            4 -> item.reason
            else -> ""
        }
    }

    override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
        val item = rows[rowIndex]
        when (columnIndex) {
            0 -> item.selected = value as? Boolean ?: false
            3 -> item.action = value as? CleanupAction ?: item.action
        }
        fireTableRowsUpdated(rowIndex, rowIndex)
    }
}
