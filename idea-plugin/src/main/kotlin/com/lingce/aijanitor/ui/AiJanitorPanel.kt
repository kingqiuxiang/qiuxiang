package com.lingce.aijanitor.ui

import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.lingce.aijanitor.core.JanitorController
import com.lingce.aijanitor.model.CleanupAction
import com.lingce.aijanitor.settings.AiJanitorConfigurable
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.DefaultCellEditor
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.table.DefaultTableCellRenderer

class AiJanitorPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val controller = JanitorController.getInstance(project)
    private val model = ScanTableModel()
    private val table = JBTable(model)
    private val statusLabel = JBLabel("点击「扫描项目」开始。")

    private val listener = JanitorController.Listener { items, message ->
        model.setItems(items)
        statusLabel.text = message
    }

    init {
        add(buildToolbar(), BorderLayout.NORTH)
        configureTable()
        add(JBScrollPane(table), BorderLayout.CENTER)
        add(statusLabel.also { it.border = javax.swing.BorderFactory.createEmptyBorder(4, 8, 4, 8) }, BorderLayout.SOUTH)
        controller.addListener(listener)
        model.setItems(controller.items)
    }

    fun dispose() {
        controller.removeListener(listener)
    }

    private fun buildToolbar(): JPanel {
        val bar = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4))
        bar.add(button("扫描项目") { controller.scan() })
        bar.add(button("全选") { model.setAllSelected(true) })
        bar.add(button("全不选") { model.setAllSelected(false) })
        bar.add(button("应用所选操作") { applySelected() })
        bar.add(button("一键删除所选") { quickApply(CleanupAction.DELETE, "删除") })
        bar.add(button("一键转存所选") { quickApply(CleanupAction.ARCHIVE, "转存到指定目录") })
        bar.add(button("设置") {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, AiJanitorConfigurable::class.java)
        })
        return bar
    }

    private fun button(text: String, action: () -> Unit): JButton =
        JButton(text).apply { addActionListener { action() } }

    private fun configureTable() {
        table.setShowGrid(true)
        table.rowHeight = 24
        val cm = table.columnModel
        cm.getColumn(0).maxWidth = 36
        cm.getColumn(0).minWidth = 36
        cm.getColumn(1).preferredWidth = 320
        cm.getColumn(2).preferredWidth = 110
        cm.getColumn(3).preferredWidth = 130
        cm.getColumn(4).preferredWidth = 280

        val combo = JComboBox(CleanupAction.values())
        combo.renderer = actionRenderer()
        cm.getColumn(3).cellEditor = DefaultCellEditor(combo)
        cm.getColumn(3).cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                t: javax.swing.JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int,
            ): Component {
                val text = (value as? CleanupAction)?.display ?: value?.toString().orEmpty()
                return super.getTableCellRendererComponent(t, text, isSelected, hasFocus, row, column)
            }
        }
    }

    private fun actionRenderer(): ListCellRenderer<CleanupAction> =
        ListCellRenderer { list: JList<out CleanupAction>, value: CleanupAction?, index: Int, isSelected: Boolean, _: Boolean ->
            val base = javax.swing.DefaultListCellRenderer()
            base.getListCellRendererComponent(list, value?.display ?: "", index, isSelected, false)
        }

    private fun applySelected() {
        val selected = model.selectedItems()
        if (selected.isEmpty()) {
            statusLabel.text = "请先勾选要处理的文件。"
            return
        }
        if (!confirm(selected.size, "应用所选操作")) return
        controller.applyItems(selected)
    }

    private fun quickApply(action: CleanupAction, verb: String) {
        val selected = model.selectedItems()
        if (selected.isEmpty()) {
            statusLabel.text = "请先勾选要处理的文件。"
            return
        }
        if (!confirm(selected.size, verb)) return
        selected.forEach { it.action = action }
        controller.applyItems(selected)
    }

    private fun confirm(count: Int, verb: String): Boolean {
        val result = Messages.showYesNoDialog(
            project,
            "确定要对 $count 个文件执行「$verb」吗？此操作会修改磁盘文件。",
            "AI 文件清道夫",
            Messages.getQuestionIcon(),
        )
        return result == Messages.YES
    }
}
