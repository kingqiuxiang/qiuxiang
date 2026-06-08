package com.lingce.aijanitor.ui

import com.lingce.aijanitor.model.CleanupAction
import com.lingce.aijanitor.model.FileCategory
import com.lingce.aijanitor.model.ScanItem
import javax.swing.table.AbstractTableModel

class ScanTableModel : AbstractTableModel() {

    private val columns = arrayOf("✓", "文件", "类别", "操作", "原因")
    private var allItems: MutableList<ScanItem> = mutableListOf()

    var filterText: String = ""
        set(value) {
            field = value
            fireTableDataChanged()
        }

    /** Rows currently visible in the table (after filtering). */
    val displayedRows: List<ScanItem>
        get() = if (filterText.isBlank()) allItems
        else allItems.filter { it.matchesFilter(filterText) }

    fun setItems(items: List<ScanItem>) {
        allItems = items.toMutableList()
        fireTableDataChanged()
    }

    /** Returns the item at the given displayed row index. */
    fun itemAt(row: Int): ScanItem = displayedRows[row]

    /** All selected items (from the full, unfiltered list). */
    fun selectedItems(): List<ScanItem> = allItems.filter { it.selected }

    /** Remove the given items from the model (they will reappear on next scan). */
    fun removeItems(itemsToRemove: List<ScanItem>) {
        allItems.removeAll(itemsToRemove.toSet())
        fireTableDataChanged()
    }

    /** Select / deselect the currently visible (filtered) rows. */
    fun selectVisibleRows(flag: Boolean) {
        for (item in displayedRows) {
            item.selected = flag
        }
        fireTableDataChanged()
    }

    /** Number of currently visible rows. */
    fun visibleCount(): Int = displayedRows.size

    /** Total rows before filtering. */
    fun totalCount(): Int = allItems.size

    override fun getRowCount(): Int = displayedRows.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column]

    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
        0 -> java.lang.Boolean::class.java
        2 -> FileCategory::class.java
        3 -> CleanupAction::class.java
        else -> String::class.java
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex == 0 || columnIndex == 3

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val item = displayedRows[rowIndex]
        return when (columnIndex) {
            0 -> item.selected
            1 -> item.relativePath
            2 -> item.category
            3 -> item.action
            4 -> item.reason
            else -> ""
        }
    }

    override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
        val item = displayedRows[rowIndex]
        when (columnIndex) {
            0 -> item.selected = value as? Boolean ?: false
            3 -> item.action = value as? CleanupAction ?: item.action
        }
        fireTableRowsUpdated(rowIndex, rowIndex)
    }
}

/**
 * Keyword / glob match against path, category, reason and action.
 * Supports * as wildcard (matches any sequence of characters) and ? as single-character wildcard.
 */
private fun ScanItem.matchesFilter(keyword: String): Boolean {
    val kw = keyword.trim().lowercase()
    if (kw.isEmpty()) return true

    fun matches(text: String): Boolean {
        return if (kw.contains('*') || kw.contains('?')) {
            val pattern = Regex.escape(kw).replace("\\*", ".*").replace("\\?", ".")
            text.lowercase().matches(Regex(pattern))
        } else {
            text.lowercase().contains(kw)
        }
    }

    return matches(relativePath) ||
        matches(category.display) ||
        matches(reason) ||
        matches(action.display)
}
