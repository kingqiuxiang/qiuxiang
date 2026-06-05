package io.lingce.ideaguardian.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import io.lingce.ideaguardian.model.FileCategory
import io.lingce.ideaguardian.model.ScanItem
import io.lingce.ideaguardian.service.IgnoreExcludeManager
import io.lingce.ideaguardian.settings.AiFileGuardianSettingsState
import java.awt.BorderLayout
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.AbstractTableModel
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

class ScanResultDialog(
    private val project: Project,
    scanItems: List<ScanItem>,
    private val settings: AiFileGuardianSettingsState
) : DialogWrapper(project) {
    private val rows = scanItems.map { RowState(it) }.toMutableList()
    private val model = ScanTableModel(rows)
    private val table = JTable(model)

    init {
        title = "AI File Guardian 扫描结果 (${scanItems.size} 项)"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, JBUI.scale(8)))
        panel.border = JBUI.Borders.empty(8)

        panel.add(
            JBLabel("建议：先执行“执行建议清理”，可疑文件可使用“一键转存可疑文件”或“一键删除可疑文件”。"),
            BorderLayout.NORTH
        )

        table.autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        table.columnModel.getColumn(0).preferredWidth = 48
        table.columnModel.getColumn(1).preferredWidth = 340
        table.columnModel.getColumn(2).preferredWidth = 130
        table.columnModel.getColumn(3).preferredWidth = 160
        table.columnModel.getColumn(4).preferredWidth = 320
        panel.add(JBScrollPane(table), BorderLayout.CENTER)

        val quickActions = JPanel().apply {
            add(JButton(object : AbstractAction("一键转存可疑文件") {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                    val moved = archiveSuspicious()
                    notify("已转存可疑文件 $moved 个。", NotificationType.INFORMATION)
                    model.fireTableDataChanged()
                }
            }))
            add(JButton(object : AbstractAction("一键删除可疑文件") {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                    val deleted = deleteSuspicious()
                    notify("已删除可疑文件 $deleted 个。", NotificationType.WARNING)
                    model.fireTableDataChanged()
                }
            }))
        }
        panel.add(quickActions, BorderLayout.SOUTH)
        return panel
    }

    override fun doOKAction() {
        val selected = rows.filter { it.selected && !it.done }
        val toDelete = selected.filter {
            it.item.category == FileCategory.TMP || it.item.category == FileCategory.AI_USELESS
        }
        val toIgnore = selected.filter { it.item.category == FileCategory.CONFIG_OR_AI_CONFIG }

        var deleted = 0
        toDelete.forEach {
            if (safeDelete(it.item.path)) {
                it.done = true
                deleted += 1
            }
        }

        val ignorePaths = toIgnore.map { it.item.relativePath }
        IgnoreExcludeManager().append(project, ignorePaths)
        toIgnore.forEach { it.done = true }

        notify(
            "清理完成：删除 $deleted 个；加入 ignore/exclude ${ignorePaths.size} 个。",
            NotificationType.INFORMATION
        )
        super.doOKAction()
    }

    override fun createActions(): Array<Action> {
        okAction.putValue(Action.NAME, "执行建议清理")
        cancelAction.putValue(Action.NAME, "关闭")
        return arrayOf(okAction, cancelAction)
    }

    private fun archiveSuspicious(): Int {
        val suspicious = rows.filter { it.item.category == FileCategory.SUSPICIOUS && !it.done }
        if (suspicious.isEmpty()) return 0
        val base = project.basePath?.let(Path::of) ?: return 0
        val archiveRoot = settings.archiveDirectory.takeIf { it.isNotBlank() }?.let(Path::of)
            ?: base.resolve(".idea-file-guardian").resolve("archive")
        archiveRoot.createDirectories()

        var moved = 0
        suspicious.forEach { row ->
            try {
                val target = archiveRoot.resolve(row.item.relativePath)
                target.parent?.createDirectories()
                Files.move(row.item.path, target, StandardCopyOption.REPLACE_EXISTING)
                row.done = true
                moved += 1
            } catch (_: Exception) {
                // Ignore single-file failures and continue.
            }
        }
        return moved
    }

    private fun deleteSuspicious(): Int {
        var deleted = 0
        rows.filter { it.item.category == FileCategory.SUSPICIOUS && !it.done }
            .forEach { row ->
                if (safeDelete(row.item.path)) {
                    row.done = true
                    deleted += 1
                }
            }
        return deleted
    }

    private fun safeDelete(path: Path): Boolean {
        return try {
            if (!path.exists()) return true
            Files.delete(path)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun notify(message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("AI File Guardian")
            .createNotification(message, type)
            .notify(project)
    }

    private data class RowState(
        val item: ScanItem,
        var selected: Boolean = item.category != FileCategory.SUSPICIOUS,
        var done: Boolean = false
    )

    private class ScanTableModel(private val rows: MutableList<RowState>) : AbstractTableModel() {
        private val columns = listOf("选中", "文件", "分类", "建议动作", "原因")

        override fun getRowCount(): Int = rows.size

        override fun getColumnCount(): Int = columns.size

        override fun getColumnName(column: Int): String = columns[column]

        override fun getColumnClass(columnIndex: Int): Class<*> {
            return if (columnIndex == 0) java.lang.Boolean::class.java else String::class.java
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
            return columnIndex == 0 && !rows[rowIndex].done
        }

        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            if (columnIndex == 0) {
                rows[rowIndex].selected = (aValue as? Boolean) == true
                fireTableCellUpdated(rowIndex, columnIndex)
            }
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rows[rowIndex]
            return when (columnIndex) {
                0 -> row.selected
                1 -> row.item.relativePath
                2 -> "${row.item.category}${if (row.done) " (已处理)" else ""}"
                3 -> row.item.suggestion.name
                4 -> row.item.reason
                else -> ""
            }
        }
    }
}
