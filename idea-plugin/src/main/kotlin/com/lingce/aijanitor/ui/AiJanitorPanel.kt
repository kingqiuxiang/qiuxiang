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
import com.intellij.util.ui.JBUI
import com.lingce.aijanitor.core.JanitorController
import com.lingce.aijanitor.model.CleanupAction
import com.lingce.aijanitor.model.FileCategory
import com.lingce.aijanitor.model.ScanItem
import com.lingce.aijanitor.settings.AiJanitorConfigurable
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.BoxLayout
import javax.swing.DefaultCellEditor
import javax.swing.DefaultListCellRenderer
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.ListCellRenderer
import javax.swing.SwingUtilities

class AiJanitorPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val controller = JanitorController.getInstance(project)
    private val model = ScanTableModel()
    private val table = JBTable(model)
    private val statusLabel = JBLabel("点击「扫描项目」开始。")
    private val filterField = JBTextField(16)
    private val chipsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, JanitorTheme.gap(6), 0)).apply { isOpaque = false }

    private var lastItems: List<ScanItem> = emptyList()

    private val listener = JanitorController.Listener { items, message ->
        lastItems = items
        model.setItems(items)
        updateStatus(message)
        updateChips()
    }

    init {
        isOpaque = true
        background = JanitorTheme.WINDOW_BG
        border = JBUI.Borders.empty(JanitorTheme.gap(10))

        add(buildHeader(), BorderLayout.NORTH)
        add(buildTableCard(), BorderLayout.CENTER)
        add(buildStatusBar(), BorderLayout.SOUTH)

        controller.addListener(listener)
        model.setItems(controller.items)
        lastItems = controller.items
        updateChips()
    }

    fun dispose() {
        controller.removeListener(listener)
    }

    // ── header (title + toolbar) ─────────────────────────────────────────────

    private fun buildHeader(): JComponent {
        val card = RoundedCard(BorderLayout(0, JanitorTheme.gap(10)))
        card.border = JBUI.Borders.empty(JanitorTheme.gap(14), JanitorTheme.gap(16))

        val titleBox = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        titleBox.add(JBLabel("AI 文件清道夫").apply {
            font = JanitorTheme.titleFont()
            foreground = JanitorTheme.TEXT_PRIMARY
        })
        titleBox.add(JBLabel("识别并清理 AI 生成 / 临时 / 配置 / 可疑文件，保持本地干净").apply {
            font = JanitorTheme.subtitleFont()
            foreground = JanitorTheme.TEXT_SECONDARY
            border = JBUI.Borders.emptyTop(JanitorTheme.gap(3))
        })
        card.add(titleBox, BorderLayout.NORTH)
        card.add(buildToolbar(), BorderLayout.CENTER)

        // give the whole header some breathing room below it
        val wrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyBottom(JanitorTheme.gap(10))
            add(card, BorderLayout.CENTER)
        }
        return wrapper
    }

    private fun buildToolbar(): JComponent {
        val bar = JPanel(WrapLayout(FlowLayout.LEFT, JanitorTheme.gap(8), JanitorTheme.gap(8)))
        bar.isOpaque = false

        bar.add(pill("扫描项目", PillButton.Style.PRIMARY) { controller.scan() })

        filterField.emptyText.text = "关键词筛选…"
        filterField.putClientProperty("JTextField.variant", "search")
        filterField.addKeyListener(object : KeyAdapter() {
            override fun keyReleased(e: KeyEvent?) {
                model.filterText = filterField.text
                updateStatus()
            }
        })
        bar.add(filterField)

        bar.add(pill("全选", PillButton.Style.GHOST) {
            model.selectVisibleRows(true)
            updateStatus()
        })
        bar.add(pill("全不选", PillButton.Style.GHOST) {
            model.selectVisibleRows(false)
            updateStatus()
        })
        bar.add(buildBatchActionButton())
        bar.add(pill("设置", PillButton.Style.GHOST) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, AiJanitorConfigurable::class.java)
        })
        return bar
    }

    /** Creates a pill button whose action lambda runs in this panel's scope. */
    private fun pill(text: String, style: PillButton.Style, action: () -> Unit): PillButton {
        val button = PillButton(text, style)
        button.addActionListener { action() }
        return button
    }

    /** Consolidated "一键操作 ▾" dropdown holding every batch action. */
    private fun buildBatchActionButton(): JComponent {
        val btn = PillButton("一键操作 ▾", PillButton.Style.SECONDARY)
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

        btn.addActionListener { popup.show(btn, 0, btn.height) }
        return btn
    }

    // ── center (table) ───────────────────────────────────────────────────────

    private fun buildTableCard(): JComponent {
        configureTable()
        val scroll = JBScrollPane(table).apply {
            border = JBUI.Borders.empty()
            isOpaque = false
            viewport.isOpaque = true
            viewport.background = JanitorTheme.CARD_BG
        }
        val card = RoundedCard(BorderLayout())
        card.border = JBUI.Borders.empty(JanitorTheme.gap(6))
        card.add(scroll, BorderLayout.CENTER)
        return card
    }

    private fun configureTable() {
        JanitorTableRenderers.styleTable(table)

        val cm = table.columnModel
        cm.getColumn(0).cellRenderer = JanitorTableRenderers.checkboxRenderer()
        cm.getColumn(1).cellRenderer = JanitorTableRenderers.pathRenderer()
        cm.getColumn(2).cellRenderer = JanitorTableRenderers.categoryRenderer()
        cm.getColumn(3).cellRenderer = JanitorTableRenderers.actionRenderer()
        cm.getColumn(4).cellRenderer = JanitorTableRenderers.reasonRenderer()

        cm.getColumn(0).minWidth = JBUI.scale(44)
        cm.getColumn(0).maxWidth = JBUI.scale(44)
        cm.getColumn(1).preferredWidth = JBUI.scale(340)
        cm.getColumn(2).preferredWidth = JBUI.scale(120)
        cm.getColumn(3).preferredWidth = JBUI.scale(140)
        cm.getColumn(4).preferredWidth = JBUI.scale(280)

        val combo = JComboBox(CleanupAction.values())
        combo.renderer = comboRenderer()
        cm.getColumn(3).cellEditor = DefaultCellEditor(combo)

        // double-click → open file in editor; single click selects the row
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val row = table.rowAtPoint(e.point)
                if (row < 0) return
                table.setRowSelectionInterval(row, row)
                if (e.clickCount == 2) {
                    val item = model.itemAt(row)
                    if (item.file.isValid) {
                        FileEditorManager.getInstance(project).openFile(item.file, true)
                    }
                }
            }
        })

        // right-click → context menu
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

                popup.add(JMenuItem("复制文件路径").apply {
                    addActionListener {
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(item.relativePath), null)
                        updateStatus("已复制: ${item.relativePath}")
                    }
                })
                popup.add(JMenuItem("复制文件名").apply {
                    addActionListener {
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(item.file.name), null)
                        updateStatus("已复制: ${item.file.name}")
                    }
                })
                popup.add(JMenuItem("打开文件").apply {
                    addActionListener {
                        if (item.file.isValid) FileEditorManager.getInstance(project).openFile(item.file, true)
                    }
                })
                popup.addSeparator()
                popup.add(JMenuItem("忽略本次").apply { addActionListener { ignoreThisTime(listOf(item)) } })
                popup.add(JMenuItem("以后均忽略").apply { addActionListener { ignorePermanently(listOf(item)) } })
                popup.addSeparator()
                popup.add(JMenuItem("AI 确认").apply { addActionListener { aiConfirmFile(item) } })

                if (item.action == CleanupAction.IGNORE) {
                    popup.addSeparator()
                    popup.add(JMenuItem("此操作将文件路径加入 .git/info/exclude").apply { isEnabled = false })
                }
                popup.show(table, e.x, e.y)
            }
        })

        // tooltip on the action column for IGNORE rows
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
                    table.toolTipText = "文件路径将被写入：${excludeFileHint()}（路径：${item.relativePath}）"
                } else {
                    table.toolTipText = null
                }
            }
        })
    }

    private fun excludeFileHint(): String {
        val base = project.guessProjectDir() ?: return ".git/info/exclude"
        val gitChild = base.findChild(".git") ?: return ".git/info/exclude"
        if (gitChild.isDirectory) return ".git/info/exclude"
        return try {
            val content = String(gitChild.contentsToByteArray()).trim()
            val prefix = "gitdir: "
            if (content.startsWith(prefix)) {
                val resolved = File(base.path, content.removePrefix(prefix).trim()).canonicalPath
                val rel = resolved.removePrefix(File(base.path).canonicalPath + File.separator)
                "$rel/info/exclude"
            } else ".git/info/exclude"
        } catch (_: Exception) {
            ".git/info/exclude"
        }
    }

    private fun comboRenderer(): ListCellRenderer<CleanupAction> =
        ListCellRenderer { list: JList<out CleanupAction>, value: CleanupAction?, index: Int, isSelected: Boolean, _: Boolean ->
            DefaultListCellRenderer().getListCellRendererComponent(list, value?.display ?: "", index, isSelected, false)
        }

    // ── status bar ───────────────────────────────────────────────────────────

    private fun buildStatusBar(): JComponent {
        statusLabel.font = JanitorTheme.font(12)
        statusLabel.foreground = JanitorTheme.TEXT_SECONDARY
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(JanitorTheme.gap(6), JanitorTheme.gap(8), 0, JanitorTheme.gap(8))
            add(statusLabel, BorderLayout.WEST)
            add(chipsPanel, BorderLayout.EAST)
        }
    }

    private fun updateChips() {
        chipsPanel.removeAll()
        for (category in FileCategory.values()) {
            if (category == FileCategory.NORMAL) continue
            val count = lastItems.count { it.category == category }
            if (count == 0) continue
            val accent = JanitorTheme.categoryColors(category).first
            chipsPanel.add(CountChip("${category.display} $count", accent))
        }
        chipsPanel.revalidate()
        chipsPanel.repaint()
    }

    private fun updateStatus(message: String? = null) {
        val sb = StringBuilder(message ?: statusLabel.text.split(" · ").firstOrNull().orEmpty())
        val total = model.totalCount()
        val visible = model.visibleCount()
        if (total > 0) {
            sb.append(" · 共 $total 个")
            if (visible != total) sb.append("，筛选出 $visible 个")
            val selCount = model.selectedItems().size
            if (selCount > 0) sb.append("，已选 $selCount 个")
        }
        statusLabel.text = sb.toString()
    }

    // ── actions ─────────────────────────────────────────────────────────────

    private fun applySelected() {
        val selected = model.selectedItems()
        if (selected.isEmpty()) {
            updateStatus("请先勾选要处理的文件。")
            return
        }
        if (!confirm(selected.size, "应用所选操作")) return
        controller.applyItems(selected)
    }

    private fun quickApply(action: CleanupAction, verb: String) {
        val selected = model.selectedItems()
        if (selected.isEmpty()) {
            updateStatus("请先勾选要处理的文件。")
            return
        }
        if (!confirm(selected.size, verb)) return
        selected.forEach { it.action = action }
        controller.applyItems(selected)
    }

    private fun ignoreThisTime(selected: List<ScanItem>) {
        if (selected.isEmpty()) {
            updateStatus("请先勾选要忽略的文件。")
            return
        }
        model.removeItems(selected)
        lastItems = lastItems - selected.toSet()
        updateChips()
        updateStatus("已忽略 ${selected.size} 个文件（下次扫描仍会出现）。")
    }

    private fun ignorePermanently(selected: List<ScanItem>) {
        if (selected.isEmpty()) {
            updateStatus("请先勾选要忽略的文件。")
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
            settings.permanentIgnorePatterns = (existing + toAdd).joinToString(", ")
        }
        model.removeItems(selected)
        lastItems = lastItems - selected.toSet()
        updateChips()
        updateStatus("已永久忽略 ${selected.size} 个文件。")
    }

    private fun aiConfirmFile(item: ScanItem) {
        if (!item.file.isValid) {
            updateStatus("文件已失效，无法分析。")
            return
        }
        updateStatus("正在调用 AI 分析 ${item.relativePath}…")
        val snippet = try {
            if (item.file.length > 1_000_000L || item.file.fileType.isBinary) "(二进制或大文件)"
            else String(item.file.contentsToByteArray(), 0, minOf(item.file.length.toInt(), 4096), item.file.charset)
        } catch (_: Exception) {
            "(无法读取文件内容)"
        }

        controller.analyzeSingleFile(item, snippet) { result ->
            ApplicationManager.getApplication().invokeLater {
                Messages.showMessageDialog(project, result, "AI 文件分析结果", Messages.getInformationIcon())
                updateStatus("AI 分析完成：${item.relativePath}")
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

/**
 * A [FlowLayout] that wraps its rows and reports the correct preferred height,
 * so toolbar pills flow onto multiple lines gracefully in a narrow tool window.
 */
private class WrapLayout(align: Int, hgap: Int, vgap: Int) : FlowLayout(align, hgap, vgap) {

    override fun preferredLayoutSize(target: Container): Dimension = layoutSize(target, true)

    override fun minimumLayoutSize(target: Container): Dimension =
        layoutSize(target, false).also { it.width -= hgap + 1 }

    private fun layoutSize(target: Container, preferred: Boolean): Dimension {
        synchronized(target.treeLock) {
            var container: Container = target
            while (container.size.width == 0 && container.parent != null) container = container.parent
            var targetWidth = container.size.width
            if (targetWidth == 0) targetWidth = Int.MAX_VALUE

            val insets = target.insets
            val horizontalInsetsAndGap = insets.left + insets.right + hgap * 2
            val maxWidth = targetWidth - horizontalInsetsAndGap

            val dim = Dimension(0, 0)
            var rowWidth = 0
            var rowHeight = 0
            for (i in 0 until target.componentCount) {
                val m = target.getComponent(i)
                if (!m.isVisible) continue
                val d = if (preferred) m.preferredSize else m.minimumSize
                if (rowWidth + d.width > maxWidth) {
                    addRow(dim, rowWidth, rowHeight)
                    rowWidth = 0
                    rowHeight = 0
                }
                if (rowWidth != 0) rowWidth += hgap
                rowWidth += d.width
                rowHeight = maxOf(rowHeight, d.height)
            }
            addRow(dim, rowWidth, rowHeight)

            dim.width += horizontalInsetsAndGap
            dim.height += insets.top + insets.bottom + vgap * 2

            val scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, target)
            if (scrollPane != null && target.isValid) dim.width -= hgap + 1
            return dim
        }
    }

    private fun addRow(dim: Dimension, rowWidth: Int, rowHeight: Int) {
        dim.width = maxOf(dim.width, rowWidth)
        if (dim.height > 0) dim.height += vgap
        dim.height += rowHeight
    }
}
