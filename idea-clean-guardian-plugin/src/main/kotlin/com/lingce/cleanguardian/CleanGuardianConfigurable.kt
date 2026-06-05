package com.lingce.cleanguardian

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class CleanGuardianConfigurable : Configurable {
    private lateinit var panel: JPanel
    private lateinit var apiBaseUrlField: JBTextField
    private lateinit var apiKeyField: JBPasswordField
    private lateinit var modelField: JBTextField
    private lateinit var quarantineDirectoryField: JBTextField
    private lateinit var autoDeleteTmpCheckbox: JBCheckBox
    private lateinit var autoDeleteAiCheckbox: JBCheckBox
    private lateinit var scanOnOpenCheckbox: JBCheckBox
    private lateinit var realtimeMonitorCheckbox: JBCheckBox
    private lateinit var maxFileSizeSpinner: JSpinner

    override fun getDisplayName(): String = "Clean Guardian"

    override fun createComponent(): JComponent {
        panel = JPanel(GridBagLayout())
        var row = 0

        apiBaseUrlField = JBTextField()
        apiKeyField = JBPasswordField()
        modelField = JBTextField()
        quarantineDirectoryField = JBTextField()
        autoDeleteTmpCheckbox = JBCheckBox("自动删除 tmp / 临时文件")
        autoDeleteAiCheckbox = JBCheckBox("自动删除 AI 生成的无用文件")
        scanOnOpenCheckbox = JBCheckBox("项目打开时自动扫描")
        realtimeMonitorCheckbox = JBCheckBox("实时监听新增/变更文件")
        maxFileSizeSpinner = JSpinner(SpinnerNumberModel(256, 16, 10 * 1024, 16))

        addRow("AI API Base URL", apiBaseUrlField, row++)
        addRow("AI API Key", apiKeyField, row++)
        addRow("AI Model", modelField, row++)
        addRow("可疑文件转存目录", quarantineDirectoryField, row++)
        addRow("最大扫描文件大小(KB)", maxFileSizeSpinner, row++)
        addFullWidth(autoDeleteTmpCheckbox, row++)
        addFullWidth(autoDeleteAiCheckbox, row++)
        addFullWidth(scanOnOpenCheckbox, row++)
        addFullWidth(realtimeMonitorCheckbox, row++)

        val hint = JBLabel("说明：项目配置文件与 AI 配置文件会自动加入 .clean-guardian/ignore/exclude/paths.txt")
        addFullWidth(hint, row++)

        val filler = JPanel()
        val c = GridBagConstraints()
        c.gridx = 0
        c.gridy = row
        c.gridwidth = 2
        c.weightx = 1.0
        c.weighty = 1.0
        c.fill = GridBagConstraints.BOTH
        panel.add(filler, c)

        reset()
        return panel
    }

    override fun isModified(): Boolean {
        val s = CleanGuardianSettingsService.getInstance().state
        return apiBaseUrlField.text.trim() != s.apiBaseUrl.trim() ||
            String(apiKeyField.password).trim() != s.apiKey.trim() ||
            modelField.text.trim() != s.model.trim() ||
            quarantineDirectoryField.text.trim() != s.quarantineDirectory.trim() ||
            autoDeleteTmpCheckbox.isSelected != s.autoDeleteTmpFiles ||
            autoDeleteAiCheckbox.isSelected != s.autoDeleteAiGarbageFiles ||
            scanOnOpenCheckbox.isSelected != s.scanOnProjectOpen ||
            realtimeMonitorCheckbox.isSelected != s.enableRealtimeMonitor ||
            (maxFileSizeSpinner.value as Int) != s.maxFileSizeKb
    }

    override fun apply() {
        val service = CleanGuardianSettingsService.getInstance()
        service.state.apiBaseUrl = apiBaseUrlField.text.trim()
        service.state.apiKey = String(apiKeyField.password).trim()
        service.state.model = modelField.text.trim().ifBlank { "gpt-4o-mini" }
        service.state.quarantineDirectory = quarantineDirectoryField.text.trim()
            .ifBlank { System.getProperty("user.home") + "/.clean-guardian-quarantine" }
        service.state.autoDeleteTmpFiles = autoDeleteTmpCheckbox.isSelected
        service.state.autoDeleteAiGarbageFiles = autoDeleteAiCheckbox.isSelected
        service.state.scanOnProjectOpen = scanOnOpenCheckbox.isSelected
        service.state.enableRealtimeMonitor = realtimeMonitorCheckbox.isSelected
        service.state.maxFileSizeKb = maxFileSizeSpinner.value as Int
    }

    override fun reset() {
        val s = CleanGuardianSettingsService.getInstance().state
        apiBaseUrlField.text = s.apiBaseUrl
        apiKeyField.text = s.apiKey
        modelField.text = s.model
        quarantineDirectoryField.text = s.quarantineDirectory
        autoDeleteTmpCheckbox.isSelected = s.autoDeleteTmpFiles
        autoDeleteAiCheckbox.isSelected = s.autoDeleteAiGarbageFiles
        scanOnOpenCheckbox.isSelected = s.scanOnProjectOpen
        realtimeMonitorCheckbox.isSelected = s.enableRealtimeMonitor
        maxFileSizeSpinner.value = s.maxFileSizeKb
    }

    private fun addRow(label: String, component: JComponent, row: Int) {
        val labelConstraints = GridBagConstraints()
        labelConstraints.gridx = 0
        labelConstraints.gridy = row
        labelConstraints.anchor = GridBagConstraints.WEST
        labelConstraints.insets = Insets(6, 8, 6, 8)
        panel.add(JBLabel(label), labelConstraints)

        val fieldConstraints = GridBagConstraints()
        fieldConstraints.gridx = 1
        fieldConstraints.gridy = row
        fieldConstraints.weightx = 1.0
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL
        fieldConstraints.insets = Insets(6, 0, 6, 8)
        panel.add(component, fieldConstraints)
    }

    private fun addFullWidth(component: JComponent, row: Int) {
        val c = GridBagConstraints()
        c.gridx = 0
        c.gridy = row
        c.gridwidth = 2
        c.anchor = GridBagConstraints.WEST
        c.fill = GridBagConstraints.HORIZONTAL
        c.insets = Insets(4, 8, 4, 8)
        panel.add(component, c)
    }
}
