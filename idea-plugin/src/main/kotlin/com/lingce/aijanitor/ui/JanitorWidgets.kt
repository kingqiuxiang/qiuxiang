package com.lingce.aijanitor.ui

import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.LayoutManager
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A fully custom-painted, Apple-style pill button.
 *
 * The native Swing look is suppressed entirely; the background is a
 * fully-rounded ("pill") shape that animates smoothly between its
 * resting, hover and pressed colours via a short [Timer] driven blend.
 *
 * Visual styling is selected through [Style] and all colours come from
 * [JanitorTheme] so the button stays theme-aware.
 */
class PillButton(
    text: String,
    private val style: Style = Style.SECONDARY,
) : JButton(text) {

    /** Visual variants mapped onto the shared design-system palette. */
    enum class Style { PRIMARY, SECONDARY, DANGER, GHOST }

    private var hovered = false
    private var pressed = false

    private var currentBg: Color = frozen(restingColor())
    private var targetBg: Color = currentBg

    private val animator = Timer(ANIM_TICK_MS) { step() }

    init {
        isContentAreaFilled = false
        isFocusPainted = false
        isBorderPainted = false
        isOpaque = false
        isRolloverEnabled = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        font = JanitorTheme.font(13, bold = style == Style.PRIMARY || style == Style.DANGER)
        border = JBUI.Borders.empty(7, 16)

        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                hovered = true
                animateToTarget()
            }

            override fun mouseExited(e: MouseEvent) {
                hovered = false
                pressed = false
                animateToTarget()
            }

            override fun mousePressed(e: MouseEvent) {
                pressed = true
                animateToTarget()
            }

            override fun mouseReleased(e: MouseEvent) {
                pressed = false
                animateToTarget()
            }
        })
    }

    override fun setEnabled(b: Boolean) {
        super.setEnabled(b)
        if (!b) {
            hovered = false
            pressed = false
        }
        animateToTarget()
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            JanitorTheme.antialias(g2)
            val w = width
            val h = height
            val arc = min(JBUI.scale(JanitorTheme.BADGE_RADIUS), h)

            g2.color = currentBg
            g2.fillRoundRect(0, 0, w, h, arc, arc)

            g2.font = font
            val fm = g2.fontMetrics
            val label = text ?: ""
            val tx = (w - fm.stringWidth(label)) / 2
            val ty = (h - fm.height) / 2 + fm.ascent
            g2.color = textColor()
            g2.drawString(label, tx, ty)
        } finally {
            g2.dispose()
        }
    }

    private fun animateToTarget() {
        targetBg = frozen(desiredColor())
        if (!animator.isRunning) animator.start()
    }

    private fun step() {
        val next = JanitorTheme.blend(currentBg, targetBg, ANIM_FRACTION)
        currentBg = next
        if (near(currentBg, targetBg)) {
            currentBg = targetBg
            animator.stop()
        }
        repaint()
    }

    private fun desiredColor(): Color {
        if (!isEnabled) return restingColor()
        return when {
            pressed -> pressedColor()
            hovered -> hoverColor()
            else -> restingColor()
        }
    }

    private fun restingColor(): Color = when (style) {
        Style.PRIMARY -> JanitorTheme.ACCENT
        Style.DANGER -> JanitorTheme.DANGER
        Style.SECONDARY -> JanitorTheme.SECONDARY_BG
        Style.GHOST -> TRANSPARENT
    }

    private fun hoverColor(): Color = when (style) {
        Style.PRIMARY -> JanitorTheme.ACCENT_HOVER
        Style.DANGER -> JanitorTheme.DANGER_HOVER
        Style.SECONDARY -> JanitorTheme.SECONDARY_BG_HOVER
        Style.GHOST -> JanitorTheme.HOVER_WASH
    }

    private fun pressedColor(): Color = when (style) {
        Style.PRIMARY -> JanitorTheme.ACCENT_PRESSED
        Style.DANGER -> JanitorTheme.DANGER_PRESSED
        Style.SECONDARY -> JanitorTheme.SECONDARY_BG_PRESSED
        Style.GHOST -> JanitorTheme.SECONDARY_BG_PRESSED
    }

    private fun textColor(): Color {
        val base = when (style) {
            Style.PRIMARY -> JanitorTheme.ON_ACCENT
            Style.DANGER -> JanitorTheme.ON_ACCENT
            Style.SECONDARY -> JanitorTheme.TEXT_PRIMARY
            Style.GHOST -> JanitorTheme.TEXT_PRIMARY
        }
        return if (isEnabled) {
            Color(base.red, base.green, base.blue, base.alpha)
        } else {
            Color(base.red, base.green, base.blue, (base.alpha * 0.6f).toInt())
        }
    }

    private companion object {
        const val ANIM_TICK_MS = 15
        const val ANIM_FRACTION = 0.34f
        val TRANSPARENT = Color(0, 0, 0, 0)

        fun frozen(c: Color): Color = Color(c.red, c.green, c.blue, c.alpha)

        fun near(a: Color, b: Color): Boolean =
            abs(a.red - b.red) <= 2 &&
                abs(a.green - b.green) <= 2 &&
                abs(a.blue - b.blue) <= 2 &&
                abs(a.alpha - b.alpha) <= 2
    }
}

/**
 * An Apple-style "card" surface: a rounded, optionally hairline-bordered
 * panel painted with [JanitorTheme.CARD_BG].
 *
 * The panel is non-opaque so the rounded surface shows through behind its
 * children; child components should keep transparent backgrounds.
 */
class RoundedCard(
    layout: LayoutManager = BorderLayout(),
    private val radius: Int = JanitorTheme.CARD_RADIUS,
    private val withBorder: Boolean = true,
) : JPanel(layout) {

    init {
        isOpaque = false
        border = JBUI.Borders.empty(12)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            JanitorTheme.antialias(g2)
            val arc = JBUI.scale(radius)
            g2.color = JanitorTheme.CARD_BG
            g2.fillRoundRect(0, 0, width, height, arc, arc)
            if (withBorder) {
                val stroke = JBUI.scale(1)
                g2.color = JanitorTheme.DIVIDER
                g2.stroke = BasicStroke(stroke.toFloat())
                g2.drawRoundRect(0, 0, width - stroke, height - stroke, arc, arc)
            }
        } finally {
            g2.dispose()
        }
        super.paintComponent(g)
    }
}

/**
 * A compact rounded count "chip" for status bars: a light tint of [accent]
 * background, a solid accent dot on the left and a short label.
 *
 * Use [setData] to update its contents in place.
 */
class CountChip(
    text: String,
    accent: Color,
) : JComponent() {

    private var chipText: String = text
    private var chipAccent: Color = accent

    init {
        isOpaque = false
        font = JanitorTheme.font(11)
    }

    /** Updates the label and accent colour, then re-lays out and repaints. */
    fun setData(text: String, accent: Color) {
        chipText = text
        chipAccent = accent
        revalidate()
        repaint()
    }

    override fun getPreferredSize(): Dimension {
        val fm = getFontMetrics(JanitorTheme.font(11))
        val w = padH() * 2 + dotSize() + dotGap() + fm.stringWidth(chipText)
        val h = padV() * 2 + max(fm.height, dotSize())
        return Dimension(w, h)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            JanitorTheme.antialias(g2)
            val w = width
            val h = height
            val arc = min(JBUI.scale(JanitorTheme.BADGE_RADIUS), h)

            g2.color = JanitorTheme.blend(chipAccent, JanitorTheme.CARD_BG, 0.85f)
            g2.fillRoundRect(0, 0, w, h, arc, arc)

            val dot = dotSize()
            val dotX = padH()
            val dotY = (h - dot) / 2
            g2.color = chipAccent
            g2.fillOval(dotX, dotY, dot, dot)

            g2.font = font
            val fm = g2.fontMetrics
            val tx = padH() + dot + dotGap()
            val ty = (h - fm.height) / 2 + fm.ascent
            g2.color = JanitorTheme.TEXT_PRIMARY
            g2.drawString(chipText, tx, ty)
        } finally {
            g2.dispose()
        }
    }

    private fun padH(): Int = JanitorTheme.gap(8)
    private fun padV(): Int = JanitorTheme.gap(4)
    private fun dotGap(): Int = JanitorTheme.gap(6)
    private fun dotSize(): Int = JBUI.scale(8)
}
