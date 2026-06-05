package com.lingce.aifilecleaner.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class CleanerConfigurable : Configurable {
    private val baseUrlField = JBTextField()
    private val apiKeyField = JBPasswordField()
    private val ignoreExcludeDirField = JBTextField()
    private val suspiciousArchiveDirField = JBTextField()
    private val autoScanOnStartupBox = JBCheckBox("项目打开后自动扫描", true)
    private val autoDeleteTmpBox = JBCheckBox("自动删除 tmp/temp/备份文件", true)
    private val autoDeleteAiJunkBox = JBCheckBox("自动删除判定为 AI 垃圾文件", true)
    private val maxFileSizeKbField = JBTextField()

    override fun getDisplayName(): String = "AI File Cleaner"

    override fun createComponent(): JComponent {
        val panel = JPanel(GridBagLayout())
        var row = 0

        fun addLabelAndField(label: String, field: JComponent) {
            panel.add(
                JLabel(label),
                GridBagConstraints(
                    0,
                    row,
                    1,
                    1,
                    0.0,
                    0.0,
                    GridBagConstraints.WEST,
                    GridBagConstraints.NONE,
                    Insets(4, 0, 4, 8),
                    0,
                    0
                )
            )
            panel.add(
                field,
                GridBagConstraints(
                    1,
                    row,
                    1,
                    1,
                    1.0,
                    0.0,
                    GridBagConstraints.HORIZONTAL,
                    GridBagConstraints.HORIZONTAL,
                    Insets(4, 0, 4, 0),
                    0,
                    0
                )
            )
            row++
        }

        addLabelAndField("API Base URL:", baseUrlField)
        addLabelAndField("API Key:", apiKeyField)
        addLabelAndField("ignore/exclude 目录:", ignoreExcludeDirField)
        addLabelAndField("可疑文件转存目录:", suspiciousArchiveDirField)
        addLabelAndField("最大扫描文件大小（KB）:", maxFileSizeKbField)

        panel.add(
            autoScanOnStartupBox,
            GridBagConstraints(
                0,
                row++,
                2,
                1,
                1.0,
                0.0,
                GridBagConstraints.WEST,
                GridBagConstraints.HORIZONTAL,
                Insets(8, 0, 0, 0),
                0,
                0
            )
        )
        panel.add(
            autoDeleteTmpBox,
            GridBagConstraints(
                0,
                row++,
                2,
                1,
                1.0,
                0.0,
                GridBagConstraints.WEST,
                GridBagConstraints.HORIZONTAL,
                Insets(4, 0, 0, 0),
                0,
                0
            )
        )
        panel.add(
            autoDeleteAiJunkBox,
            GridBagConstraints(
                0,
                row,
                2,
                1,
                1.0,
                1.0,
                GridBagConstraints.NORTHWEST,
                GridBagConstraints.HORIZONTAL,
                Insets(4, 0, 0, 0),
                0,
                0
            )
        )

        reset()
        return panel
    }

    override fun isModified(): Boolean {
        val state = service<CleanerSettingsState>().state
        return baseUrlField.text.trim() != state.baseUrl.trim() ||
            String(apiKeyField.password) != state.apiKey ||
            ignoreExcludeDirField.text.trim() != state.ignoreExcludeDir.trim() ||
            suspiciousArchiveDirField.text.trim() != state.suspiciousArchiveDir.trim() ||
            autoScanOnStartupBox.isSelected != state.autoScanOnStartup ||
            autoDeleteTmpBox.isSelected != state.autoDeleteTmpFiles ||
            autoDeleteAiJunkBox.isSelected != state.autoDeleteAiJunkFiles ||
            maxFileSizeKbField.text.trim() != state.maxFileSizeKb.toString()
    }

    override fun apply() {
        val settings = service<CleanerSettingsState>().state
        val parsedMaxFileSize = maxFileSizeKbField.text.trim().toIntOrNull()
        if (parsedMaxFileSize == null || parsedMaxFileSize <= 0) {
            Messages.showErrorDialog("最大扫描文件大小必须是正整数（KB）。", "AI File Cleaner")
            return
        }

        settings.baseUrl = baseUrlField.text.trim()
        settings.apiKey = String(apiKeyField.password).trim()
        settings.ignoreExcludeDir = ignoreExcludeDirField.text.trim().ifEmpty { ".project-cleaner/ignore-exclude" }
        settings.suspiciousArchiveDir = suspiciousArchiveDirField.text.trim().ifEmpty { ".project-cleaner/suspicious-archive" }
        settings.autoScanOnStartup = autoScanOnStartupBox.isSelected
        settings.autoDeleteTmpFiles = autoDeleteTmpBox.isSelected
        settings.autoDeleteAiJunkFiles = autoDeleteAiJunkBox.isSelected
        settings.maxFileSizeKb = parsedMaxFileSize
    }

    override fun reset() {
        val settings = service<CleanerSettingsState>().state
        baseUrlField.text = settings.baseUrl
        apiKeyField.text = settings.apiKey
        ignoreExcludeDirField.text = settings.ignoreExcludeDir
        suspiciousArchiveDirField.text = settings.suspiciousArchiveDir
        autoScanOnStartupBox.isSelected = settings.autoScanOnStartup
        autoDeleteTmpBox.isSelected = settings.autoDeleteTmpFiles
        autoDeleteAiJunkBox.isSelected = settings.autoDeleteAiJunkFiles
        maxFileSizeKbField.text = settings.maxFileSizeKb.toString()
    }
}
