package com.lingce.aijanitor.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.lingce.aijanitor.model.CleanupAction
import com.lingce.aijanitor.model.FileCategory
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints

/**
 * Apple-flavoured design system for the plugin UI: soft rounded corners,
 * calm system-like colours (theme aware via [JBColor]) and consistent spacing.
 *
 * All sizes go through [JBUI.scale] so the UI stays crisp on HiDPI displays.
 */
object JanitorTheme {

    // ---- Corner radii (logical px, scale at paint time) ----
    const val CARD_RADIUS = 18
    const val BUTTON_RADIUS = 12
    const val ROW_RADIUS = 10
    const val BADGE_RADIUS = 999 // fully rounded pill

    // ---- Spacing scale ----
    fun gap(n: Int): Int = JBUI.scale(n)

    // ---- Surfaces ----
    /** App background — Apple's light "system gray 6" / dark editor backdrop. */
    val WINDOW_BG = JBColor(0xF5F5F7, 0x1E1F22)
    /** Card / elevated surface. */
    val CARD_BG = JBColor(0xFFFFFF, 0x2B2D30)
    /** Subtle hairline divider. */
    val DIVIDER = JBColor(0xE6E6EB, 0x3A3C40)
    /** Hover wash for rows / ghost buttons. */
    val HOVER_WASH = JBColor(0xEFEFF4, 0x33363B)
    /** Alternate row tint (very subtle). */
    val ROW_ALT = JBColor(0xFAFAFC, 0x303336)
    /** Selected row background (accent tinted). */
    val ROW_SELECTED = JBColor(0xE3F0FF, 0x2E4159)

    // ---- Text ----
    val TEXT_PRIMARY = JBColor(0x1D1D1F, 0xECECEC)
    val TEXT_SECONDARY = JBColor(0x86868B, 0x9AA0A6)

    // ---- Accent (Apple system blue) ----
    val ACCENT = JBColor(0x007AFF, 0x0A84FF)
    val ACCENT_HOVER = JBColor(0x2B8BFF, 0x3A9BFF)
    val ACCENT_PRESSED = JBColor(0x0062CC, 0x0A6CE0)
    val ON_ACCENT: Color = Color(0xFFFFFF)

    // ---- Destructive (Apple system red) ----
    val DANGER = JBColor(0xFF3B30, 0xFF453A)
    val DANGER_HOVER = JBColor(0xFF564D, 0xFF6961)
    val DANGER_PRESSED = JBColor(0xD70015, 0xD70015)

    // ---- Secondary button fill ----
    val SECONDARY_BG = JBColor(0xEFEFF4, 0x3A3D41)
    val SECONDARY_BG_HOVER = JBColor(0xE5E5EA, 0x45484D)
    val SECONDARY_BG_PRESSED = JBColor(0xD9D9DF, 0x4F5358)

    // ---- Fonts ----
    fun font(size: Int = 13, bold: Boolean = false): Font {
        val base = JBFont.label().deriveFont(JBUI.scale(size).toFloat())
        return if (bold) base.deriveFont(Font.BOLD) else base
    }

    fun titleFont(): Font = font(17, bold = true)
    fun subtitleFont(): Font = font(12)

    /** Foreground + background colours used to paint a category badge. */
    fun categoryColors(category: FileCategory): Pair<Color, Color> = when (category) {
        FileCategory.AI_JUNK -> JBColor(0xD70015, 0xFF6961) to JBColor(0xFFE5E3, 0x4A2B2B)
        FileCategory.TEMP -> JBColor(0xB25000, 0xFFB066) to JBColor(0xFFEAD6, 0x4A3A26)
        FileCategory.PROJECT_CONFIG -> JBColor(0x0062CC, 0x6FB3FF) to JBColor(0xDCEBFF, 0x26354A)
        FileCategory.AI_CONFIG -> JBColor(0x7A3FCB, 0xC79BFF) to JBColor(0xEEE3FF, 0x39294A)
        FileCategory.SUSPICIOUS -> JBColor(0x8A6D00, 0xFFD60A) to JBColor(0xFFF4CC, 0x44401F)
        FileCategory.NORMAL -> JBColor(0x2E7D32, 0x7EE787) to JBColor(0xE2F5E4, 0x26402A)
    }

    /** Foreground colour used to tint an action label. */
    fun actionColor(action: CleanupAction): Color = when (action) {
        CleanupAction.DELETE -> DANGER
        CleanupAction.ARCHIVE -> JBColor(0xB25000, 0xFFB066)
        CleanupAction.IGNORE -> ACCENT
        CleanupAction.KEEP -> TEXT_SECONDARY
    }

    /** Enables high quality antialiasing for custom painting. */
    fun antialias(g: Graphics2D) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    }

    /** Linearly blends two colours (0f = a, 1f = b). Useful for hover animation. */
    fun blend(a: Color, b: Color, t: Float): Color {
        val c = t.coerceIn(0f, 1f)
        return Color(
            (a.red + (b.red - a.red) * c).toInt(),
            (a.green + (b.green - a.green) * c).toInt(),
            (a.blue + (b.blue - a.blue) * c).toInt(),
            (a.alpha + (b.alpha - a.alpha) * c).toInt(),
        )
    }
}
