package com.lingce.cleanguard.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class CleanGuardConfigurable : Configurable {

    private var panel: JPanel? = null
    private val enabledBox = JBCheckBox("启用 Clean Guard")
    private val apiKeyField = JPasswordField()
    private val baseUrlField = JBTextField()
    private val modelField = JBTextField()
    private val quarantineField = JBTextField()
    private val autoCleanTmpBox = JBCheckBox("自动清理临时文件")
    private val autoCleanAiBox = JBCheckBox("自动清理无用 AI 生成文件")
    private val autoExcludeBox = JBCheckBox("自动将配置文件加入 ignore/exclude")
    private val useAiBox = JBCheckBox("启用 AI 深度检测（需配置 API）")
    private val notifyBox = JBCheckBox("操作后弹出通知")
    private val maxBytesSpinner = JSpinner(SpinnerNumberModel(32768, 1024, 524288, 1024))

    override fun getDisplayName(): String = "Clean Guard"

    override fun createComponent(): JComponent {
        panel = FormBuilder.createFormBuilder()
            .addComponent(enabledBox)
            .addLabeledComponent("API Key:", apiKeyField)
            .addLabeledComponent("Base URL:", baseUrlField)
            .addLabeledComponent("Model:", modelField)
            .addLabeledComponent("隔离目录（相对项目根）:", quarantineField)
            .addComponent(autoCleanTmpBox)
            .addComponent(autoCleanAiBox)
            .addComponent(autoExcludeBox)
            .addComponent(useAiBox)
            .addComponent(notifyBox)
            .addLabeledComponent("AI 检测最大文件字节:", maxBytesSpinner)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        return panel!!
    }

    override fun isModified(): Boolean {
        val s = CleanGuardSettings.getInstance().state
        return enabledBox.isSelected != s.enabled ||
            String(apiKeyField.password) != s.apiKey ||
            baseUrlField.text != s.baseUrl ||
            modelField.text != s.model ||
            quarantineField.text != s.quarantineDir ||
            autoCleanTmpBox.isSelected != s.autoCleanTmp ||
            autoCleanAiBox.isSelected != s.autoCleanAiGenerated ||
            autoExcludeBox.isSelected != s.autoExcludeConfig ||
            useAiBox.isSelected != s.useAiDetection ||
            notifyBox.isSelected != s.notifyOnAction ||
            maxBytesSpinner.value != s.maxAiFileBytes
    }

    override fun apply() {
        val s = CleanGuardSettings.getInstance().state
        s.enabled = enabledBox.isSelected
        s.apiKey = String(apiKeyField.password)
        s.baseUrl = baseUrlField.text.trim().removeSuffix("/")
        s.model = modelField.text.trim()
        s.quarantineDir = quarantineField.text.trim()
        s.autoCleanTmp = autoCleanTmpBox.isSelected
        s.autoCleanAiGenerated = autoCleanAiBox.isSelected
        s.autoExcludeConfig = autoExcludeBox.isSelected
        s.useAiDetection = useAiBox.isSelected
        s.notifyOnAction = notifyBox.isSelected
        s.maxAiFileBytes = maxBytesSpinner.value as Int
    }

    override fun reset() {
        val s = CleanGuardSettings.getInstance().state
        enabledBox.isSelected = s.enabled
        apiKeyField.text = s.apiKey
        baseUrlField.text = s.baseUrl
        modelField.text = s.model
        quarantineField.text = s.quarantineDir
        autoCleanTmpBox.isSelected = s.autoCleanTmp
        autoCleanAiBox.isSelected = s.autoCleanAiGenerated
        autoExcludeBox.isSelected = s.autoExcludeConfig
        useAiBox.isSelected = s.useAiDetection
        notifyBox.isSelected = s.notifyOnAction
        maxBytesSpinner.value = s.maxAiFileBytes
    }
}
