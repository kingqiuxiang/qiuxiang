package com.lingce.aijanitor.ui

import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.lingce.aijanitor.model.CleanupAction
import com.lingce.aijanitor.model.FileCategory
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.JTableHeader
import javax.swing.table.TableCellRenderer

/**
 * Custom [TableCellRenderer] factory + [JBTable] styling that give the scan
 * results table an Apple-like, rounded and smooth appearance.
 *
 * The integrator wires the renderers per column:
 *  - column 0 → [checkboxRenderer] (Boolean)
 *  - column 1 → [pathRenderer] (String relative path)
 *  - column 2 → [categoryRenderer] ([FileCategory])
 *  - column 3 → [actionRenderer] ([CleanupAction])
 *  - column 4 → [reasonRenderer] (String reason)
 *
 * Every renderer fills its whole cell with the correct row background first
 * (selection / striping) and then paints content on top, so the table looks
 * consistent regardless of column.
 */
object JanitorTableRenderers {

    /**
     * Renders a [FileCategory] as a rounded filled pill badge: background and
     * text colours come from [JanitorTheme.categoryColors]. Falls back to
     * `value.toString()` for unexpected value types.
     */
    fun categoryRenderer(): TableCellRenderer = CategoryBadgeRenderer()

    /**
     * Renders a [CleanupAction] as a subtle outlined pill: a 1px rounded
     * outline and faint fill tinted with [JanitorTheme.actionColor], with the
     * action label in the same colour. Falls back to `value.toString()`.
     */
    fun actionRenderer(): TableCellRenderer = ActionPillRenderer()

    /**
     * Renders a relative path string two-tone: the directory portion (up to and
     * including the last '/') in [JanitorTheme.TEXT_SECONDARY] and the file name
     * (bold) in [JanitorTheme.TEXT_PRIMARY].
     */
    fun pathRenderer(): TableCellRenderer = PathRenderer()

    /**
     * Renders plain reason text in [JanitorTheme.TEXT_SECONDARY], single line
     * with automatic ellipsis when truncated.
     */
    fun reasonRenderer(): TableCellRenderer = ReasonRenderer()

    /**
     * Renders a centered boolean checkbox whose background respects row
     * striping and selection. The cell value is a [Boolean].
     */
    fun checkboxRenderer(): TableCellRenderer = CheckboxCellRenderer()

    /**
     * Applies the Apple-flavoured look to [table]: row height, no grid, card
     * background, selection colours, fonts, and a flat custom header with a
     * hairline bottom divider.
     */
    fun styleTable(table: JBTable) {
        table.rowHeight = JBUI.scale(34)
        table.intercellSpacing = Dimension(0, 0)
        table.setShowGrid(false)
        table.fillsViewportHeight = true
        table.background = JanitorTheme.CARD_BG
        table.selectionBackground = JanitorTheme.ROW_SELECTED
        table.selectionForeground = JanitorTheme.TEXT_PRIMARY
        table.font = JanitorTheme.font(13)
        table.isFocusable = true

        val header: JTableHeader = table.tableHeader
        header.reorderingAllowed = false
        header.defaultRenderer = HeaderRenderer()
        header.preferredSize = Dimension(header.preferredSize.width, JBUI.scale(30))
    }

    /**
     * Background colour for a cell given selection state and row index:
     * selected → [JanitorTheme.ROW_SELECTED]; even row → [JanitorTheme.CARD_BG];
     * odd row → [JanitorTheme.ROW_ALT].
     */
    private fun rowBackground(isSelected: Boolean, row: Int): Color = when {
        isSelected -> JanitorTheme.ROW_SELECTED
        row % 2 == 0 -> JanitorTheme.CARD_BG
        else -> JanitorTheme.ROW_ALT
    }

    private class CategoryBadgeRenderer : JComponent(), TableCellRenderer {
        private var text: String = ""
        private var fg: Color = JanitorTheme.TEXT_PRIMARY
        private var badgeBg: Color = JanitorTheme.CARD_BG
        private var rowBg: Color = JanitorTheme.CARD_BG

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            val category = value as? FileCategory
            if (category != null) {
                text = category.display
                val colors = JanitorTheme.categoryColors(category)
                fg = colors.first
                badgeBg = colors.second
            } else {
                text = value?.toString().orEmpty()
                fg = JanitorTheme.TEXT_PRIMARY
                badgeBg = JanitorTheme.HOVER_WASH
            }
            rowBg = rowBackground(isSelected, row)
            font = JanitorTheme.font(11, bold = true)
            return this
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                JanitorTheme.antialias(g2)
                g2.color = rowBg
                g2.fillRect(0, 0, width, height)

                val leftInset = JanitorTheme.gap(8)
                val padX = JanitorTheme.gap(8)
                g2.font = font
                val fm = g2.fontMetrics
                val textW = fm.stringWidth(text)
                val badgeH = (height - JanitorTheme.gap(10)).coerceAtLeast(fm.height)
                val badgeW = textW + padX * 2
                val y = (height - badgeH) / 2f
                val arc = badgeH.toFloat()

                g2.color = badgeBg
                g2.fill(RoundRectangle2D.Float(leftInset.toFloat(), y, badgeW.toFloat(), badgeH.toFloat(), arc, arc))

                g2.color = fg
                val textY = (height - fm.height) / 2 + fm.ascent
                g2.drawString(text, leftInset + padX, textY)
            } finally {
                g2.dispose()
            }
        }
    }

    private class ActionPillRenderer : JComponent(), TableCellRenderer {
        private var text: String = ""
        private var tint: Color = JanitorTheme.TEXT_SECONDARY
        private var rowBg: Color = JanitorTheme.CARD_BG

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            val action = value as? CleanupAction
            if (action != null) {
                text = action.display
                tint = JanitorTheme.actionColor(action)
            } else {
                text = value?.toString().orEmpty()
                tint = JanitorTheme.TEXT_SECONDARY
            }
            rowBg = rowBackground(isSelected, row)
            font = JanitorTheme.font(11, bold = true)
            return this
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                JanitorTheme.antialias(g2)
                g2.color = rowBg
                g2.fillRect(0, 0, width, height)

                val leftInset = JanitorTheme.gap(8)
                val padX = JanitorTheme.gap(8)
                g2.font = font
                val fm = g2.fontMetrics
                val textW = fm.stringWidth(text)
                val badgeH = (height - JanitorTheme.gap(10)).coerceAtLeast(fm.height)
                val badgeW = textW + padX * 2
                val y = (height - badgeH) / 2f
                val arc = badgeH.toFloat()
                val shape = RoundRectangle2D.Float(
                    leftInset.toFloat() + 0.5f,
                    y + 0.5f,
                    (badgeW - 1).toFloat(),
                    (badgeH - 1).toFloat(),
                    arc,
                    arc,
                )

                g2.color = Color(tint.red, tint.green, tint.blue, 28)
                g2.fill(shape)

                g2.color = tint
                g2.stroke = java.awt.BasicStroke(JBUI.scale(1).toFloat())
                g2.draw(shape)

                val textY = (height - fm.height) / 2 + fm.ascent
                g2.drawString(text, leftInset + padX, textY)
            } finally {
                g2.dispose()
            }
        }
    }

    private class PathRenderer : JComponent(), TableCellRenderer {
        private var dirPart: String = ""
        private var namePart: String = ""
        private var rowBg: Color = JanitorTheme.CARD_BG

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            val path = value?.toString().orEmpty()
            val slash = path.lastIndexOf('/')
            if (slash >= 0) {
                dirPart = path.substring(0, slash + 1)
                namePart = path.substring(slash + 1)
            } else {
                dirPart = ""
                namePart = path
            }
            rowBg = rowBackground(isSelected, row)
            return this
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                JanitorTheme.antialias(g2)
                g2.color = rowBg
                g2.fillRect(0, 0, width, height)

                val leftPad = JanitorTheme.gap(10)
                val regular = JanitorTheme.font(13)
                val bold = JanitorTheme.font(13, bold = true)
                val fm = g2.getFontMetrics(regular)
                val baseline = (height - fm.height) / 2 + fm.ascent

                var x = leftPad
                if (dirPart.isNotEmpty()) {
                    g2.font = regular
                    g2.color = JanitorTheme.TEXT_SECONDARY
                    g2.drawString(dirPart, x, baseline)
                    x += g2.fontMetrics.stringWidth(dirPart)
                }
                g2.font = bold
                g2.color = JanitorTheme.TEXT_PRIMARY
                g2.drawString(namePart, x, baseline)
            } finally {
                g2.dispose()
            }
        }
    }

    private class ReasonRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            text = value?.toString().orEmpty()
            font = JanitorTheme.font(12)
            foreground = JanitorTheme.TEXT_SECONDARY
            background = rowBackground(isSelected, row)
            isOpaque = true
            horizontalAlignment = SwingConstants.LEFT
            border = JBUI.Borders.empty(0, JanitorTheme.gap(8))
            putClientProperty("html.disable", true)
            return this
        }
    }

    private class CheckboxCellRenderer : JCheckBox(), TableCellRenderer {
        init {
            horizontalAlignment = CENTER
            isBorderPainted = false
            isOpaque = true
        }

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            this.isSelected = value as? Boolean ?: false
            background = rowBackground(isSelected, row)
            return this
        }
    }

    private class HeaderRenderer : JLabel(), TableCellRenderer {
        init {
            isOpaque = false
        }

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            text = value?.toString().orEmpty()
            font = JanitorTheme.font(11, bold = true)
            foreground = JanitorTheme.TEXT_SECONDARY
            horizontalAlignment = if (column == 0) SwingConstants.CENTER else SwingConstants.LEFT
            border = JBUI.Borders.empty(0, JanitorTheme.gap(8))
            return this
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = JanitorTheme.CARD_BG
                g2.fillRect(0, 0, width, height)
                g2.color = JanitorTheme.DIVIDER
                val thickness = JBUI.scale(1)
                g2.fillRect(0, height - thickness, width, thickness)
            } finally {
                g2.dispose()
            }
            super.paintComponent(g)
        }
    }
}
