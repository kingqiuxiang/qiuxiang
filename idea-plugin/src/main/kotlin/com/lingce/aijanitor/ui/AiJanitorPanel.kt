package com.lingce.aijanitor.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.lingce.aijanitor.core.JanitorController
import com.lingce.aijanitor.model.CleanupAction
import com.lingce.aijanitor.model.ScanItem
import com.lingce.aijanitor.settings.AiJanitorConfigurable
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.DefaultCellEditor
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableCellRenderer

class AiJanitorPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val controller = JanitorController.getInstance(project)
    private val model = ScanTableModel()
    private val table = JBTable(model)
    private val statusLabel = JBLabel("点击「扫描项目」开始。")
    private val filterField = JBTextField(20)

    private val listener = JanitorController.Listener { items, message ->
        model.setItems(items)
        updateStatus(message)
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

    // ── toolbar ────────────────────────────────────────────────────────────

    private fun buildToolbar(): JPanel {
        val bar = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4))
        bar.add(button("扫描项目") { controller.scan() })

        // filter field
        filterField.emptyText.text = "关键词筛选…"
        filterField.addKeyListener(object : KeyAdapter() {
            override fun keyReleased(e: KeyEvent?) {
                model.filterText = filterField.text
                updateStatus()
            }
        })
        bar.add(filterField)

        bar.add(button("全选") {
            model.setAllSelected(true)
            updateStatus()
        })
        bar.add(button("全不选") { model.setAllSelected(false) })
        bar.add(buildBatchActionButton())
        bar.add(button("设置") {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, AiJanitorConfigurable::class.java)
        })
        return bar
    }

    private fun button(text: String, action: () -> Unit): JButton =
        JButton(text).apply { addActionListener { action() } }

    /** Builds the "一键操作 ▼" dropdown button that consolidates all batch actions. */
    private fun buildBatchActionButton(): JButton {
        val btn = JButton("一键操作 ▼")
        val popup = JPopupMenu()

        fun addItem(label: String, action: () -> Unit) {
            popup.add(JMenuItem(label).apply { addActionListener { action() } })
        }

        addItem("执行推荐操作") { applySelected() }
        popup.addSeparator()
        addItem("全部删除") { quickApply(CleanupAction.DELETE, "删除") }
        addItem("全部转存") { quickApply(CleanupAction.ARCHIVE, "转存到指定目录") }
        addItem("全部移入 .git/info/exclude") { quickApply(CleanupAction.IGNORE, "移入 .git/info/exclude") }
        popup.addSeparator()
        addItem("忽略本次（选中）") { ignoreThisTime(model.selectedItems()) }
        addItem("以后均忽略（选中）") { ignorePermanently(model.selectedItems()) }

        btn.addActionListener {
            popup.show(btn, 0, btn.height)
        }
        return btn
    }

    // ── table configuration ─────────────────────────────────────────────────

    private fun configureTable() {
        table.setShowGrid(true)
        table.rowHeight = 24
        val cm = table.columnModel
        cm.getColumn(0).maxWidth = 36
        cm.getColumn(0).minWidth = 36
        cm.getColumn(1).preferredWidth = 360
        cm.getColumn(2).preferredWidth = 100
        cm.getColumn(3).preferredWidth = 140
        cm.getColumn(4).preferredWidth = 280

        // action combo editor
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

        // double-click → open file in editor
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val row = table.rowAtPoint(e.point)
                if (row < 0) return
                // select the row on click so user gets visual feedback
                table.setRowSelectionInterval(row, row)
                if (e.clickCount == 2) {
                    val item = model.itemAt(row)
                    if (item.file.isValid) {
                        FileEditorManager.getInstance(project).openFile(item.file, true)
                    }
                }
            }
        })

        // right-click → popup menu
        table.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = maybeShowPopup(e)
            override fun mouseReleased(e: MouseEvent) = maybeShowPopup(e)

            private fun maybeShowPopup(e: MouseEvent) {
                if (!e.isPopupTrigger) return
                val row = table.rowAtPoint(e.point)
                if (row < 0) return
                table.setRowSelectionInterval(row, row)
                val item = model.itemAt(row)
                val popup = JPopupMenu()

                val copyPath = JMenuItem("复制文件路径").apply {
                    addActionListener {
                        val sel = StringSelection(item.relativePath)
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, null)
                        updateStatus("已复制: ${item.relativePath}")
                    }
                }
                popup.add(copyPath)

                val copyName = JMenuItem("复制文件名").apply {
                    addActionListener {
                        val sel = StringSelection(item.file.name)
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, null)
                        updateStatus("已复制: ${item.file.name}")
                    }
                }
                popup.add(copyName)

                val openFile = JMenuItem("打开文件").apply {
                    addActionListener {
                        if (item.file.isValid) {
                            FileEditorManager.getInstance(project).openFile(item.file, true)
                        }
                    }
                }
                popup.add(openFile)

                popup.addSeparator()

                val ignoreOnce = JMenuItem("忽略本次").apply {
                    addActionListener { ignoreThisTime(listOf(item)) }
                }
                popup.add(ignoreOnce)

                val ignoreForever = JMenuItem("以后均忽略").apply {
                    addActionListener { ignorePermanently(listOf(item)) }
                }
                popup.add(ignoreForever)

                popup.addSeparator()

                val aiConfirm = JMenuItem("AI 确认").apply {
                    addActionListener {
                        aiConfirmFile(item)
                    }
                }
                popup.add(aiConfirm)

                // Tooltip info for IGNORE action: show where the file will be listed
                if (item.action == CleanupAction.IGNORE) {
                    popup.addSeparator()
                    val infoItem = JMenuItem("此操作将文件路径加入 .git/info/exclude").apply {
                        isEnabled = false
                    }
                    popup.add(infoItem)
                }

                popup.show(table, e.x, e.y)
            }
        })

        // Tooltip on action column showing git exclude path for IGNORE action
        table.addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val row = table.rowAtPoint(e.point)
                val col = table.columnAtPoint(e.point)
                if (row < 0 || col != 3) {
                    table.toolTipText = null
                    return
                }
                val item = model.itemAt(row)
                if (item.action == CleanupAction.IGNORE) {
                    val base = project.guessProjectDir()
                    val gitDir = base?.findChild(".git")
                    val excludePath = if (gitDir?.isDirectory == true) {
                        ".git/info/exclude"
                    } else {
                        // worktree: read .git file to find actual git dir
                        val gitFile = base?.findChild(".git")
                        if (gitFile != null && !gitFile.isDirectory) {
                            try {
                                val content = String(gitFile.contentsToByteArray()).trim()
                                val prefix = "gitdir: "
                                if (content.startsWith(prefix)) {
                                    val resolved = File(base.path, content.removePrefix(prefix).trim()).canonicalPath
                                    val rel = resolved.removePrefix(File(base.path).canonicalPath + File.separator)
                                    "$rel/info/exclude"
                                } else null
                            } catch (_: Exception) { null }
                        } else null
                    } ?: ".git/info/exclude"
                    table.toolTipText = "文件路径将被写入：$excludePath（路径：${item.relativePath}）"
                } else {
                    table.toolTipText = null
                }
            }
        })
    }

    private fun actionRenderer(): ListCellRenderer<CleanupAction> =
        ListCellRenderer { list: JList<out CleanupAction>, value: CleanupAction?, index: Int, isSelected: Boolean, _: Boolean ->
            val base = javax.swing.DefaultListCellRenderer()
            base.getListCellRendererComponent(list, value?.display ?: "", index, isSelected, false)
        }

    // ── status ──────────────────────────────────────────────────────────────

    private fun updateStatus(message: String? = null) {
        val sb = StringBuilder(message ?: statusLabel.text.split(" | ").firstOrNull().orEmpty())
        val total = model.totalCount()
        val visible = model.visibleCount()
        if (total > 0) {
            sb.append(" | 共 $total 个")
            if (visible != total) {
                sb.append("，筛选出 $visible 个")
            }
            val selCount = model.selectedItems().size
            if (selCount > 0) {
                sb.append("，已选 $selCount 个")
            }
        }
        statusLabel.text = sb.toString()
    }

    // ── actions ─────────────────────────────────────────────────────────────

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

    private fun ignoreThisTime(selected: List<ScanItem>) {
        if (selected.isEmpty()) {
            statusLabel.text = "请先勾选要忽略的文件。"
            return
        }
        model.removeItems(selected)
        updateStatus("已忽略 ${selected.size} 个文件（下次扫描仍会出现）。")
    }

    private fun ignorePermanently(selected: List<ScanItem>) {
        if (selected.isEmpty()) {
            statusLabel.text = "请先勾选要忽略的文件。"
            return
        }
        if (!confirm(selected.size, "以后均忽略")) return
        val settings = com.lingce.aijanitor.settings.AiJanitorSettings.getInstance().state
        val names = selected.map { it.file.name }.distinct()
        val existing = settings.permanentIgnorePatterns
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val toAdd = names.filter { name -> name !in existing }
        if (toAdd.isNotEmpty()) {
            val merged = (existing + toAdd).joinToString(", ")
            settings.permanentIgnorePatterns = merged
        }
        model.removeItems(selected)
        updateStatus("已永久忽略 ${selected.size} 个文件。")
    }

    private fun aiConfirmFile(item: ScanItem) {
        if (!item.file.isValid) {
            statusLabel.text = "文件已失效，无法分析。"
            return
        }
        statusLabel.text = "正在调用 AI 分析 ${item.relativePath}…"
        val snippet = try {
            if (item.file.length > 1_000_000L || item.file.fileType.isBinary) "(二进制或大文件)"
            else String(item.file.contentsToByteArray(), 0, minOf(item.file.length.toInt(), 4096), item.file.charset)
        } catch (_: Exception) { "(无法读取文件内容)" }

        controller.analyzeSingleFile(item, snippet) { result ->
            ApplicationManager.getApplication().invokeLater {
                Messages.showMessageDialog(
                    project,
                    result,
                    "AI 文件分析结果",
                    Messages.getInformationIcon(),
                )
                statusLabel.text = "AI 分析完成：${item.relativePath}"
            }
        }
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
