package io.lingce.ideaguardian.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class AiFileGuardianConfigurable : SearchableConfigurable, Configurable.NoScroll {
    private val apiKeyField = JBTextField()
    private val baseUrlField = JBTextField()
    private val archiveDirField = JBTextField()
    private val maxApiCallsSpinner = JSpinner(SpinnerNumberModel(20, 0, 200, 1))
    private var panel: JPanel? = null

    override fun getId(): String = "io.lingce.ideaguardian.settings"

    override fun getDisplayName(): String = "AI File Guardian"

    override fun createComponent(): JComponent {
        panel = JPanel(BorderLayout()).apply {
            add(
                FormBuilder.createFormBuilder()
                    .addLabeledComponent(JLabel("API Key:"), apiKeyField, 1, false)
                    .addLabeledComponent(JLabel("Base URL:"), baseUrlField, 1, false)
                    .addLabeledComponent(JLabel("可疑文件转存目录（可为空）:"), archiveDirField, 1, false)
                    .addLabeledComponent(JLabel("每次扫描最大 API 识别次数:"), maxApiCallsSpinner, 1, false)
                    .addComponentFillVertically(JPanel(), 0)
                    .panel,
                BorderLayout.NORTH
            )
        }
        reset()
        return panel as JPanel
    }

    override fun isModified(): Boolean {
        val state = service<AiFileGuardianSettingsService>().state
        return apiKeyField.text != state.apiKey ||
            baseUrlField.text != state.baseUrl ||
            archiveDirField.text != state.archiveDirectory ||
            (maxApiCallsSpinner.value as Int) != state.maxApiDetectionsPerScan
    }

    override fun apply() {
        val service = service<AiFileGuardianSettingsService>()
        service.state.apiKey = apiKeyField.text.trim()
        service.state.baseUrl = baseUrlField.text.trim()
        service.state.archiveDirectory = archiveDirField.text.trim()
        service.state.maxApiDetectionsPerScan = maxApiCallsSpinner.value as Int
    }

    override fun reset() {
        val state = service<AiFileGuardianSettingsService>().state
        apiKeyField.text = state.apiKey
        baseUrlField.text = state.baseUrl
        archiveDirField.text = state.archiveDirectory
        maxApiCallsSpinner.value = state.maxApiDetectionsPerScan
    }
}
