package com.lingce.cleankeeper.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class CleanKeeperConfigurable : Configurable {

    private var panel: JPanel? = null
    private val enabledBox = JBCheckBox("启用插件")
    private val autoScanBox = JBCheckBox("导入/新建文件时自动扫描")
    private val autoDeleteTmpBox = JBCheckBox("自动删除临时文件")
    private val autoDeleteAiBox = JBCheckBox("自动删除无用 AI 产物")
    private val autoIgnoreBox = JBCheckBox("自动将配置类文件加入 Ignore/Exclude")
    private val useAiBox = JBCheckBox("启用 AI 辅助分类（需配置 API）")
    private val notifyBox = JBCheckBox("显示操作通知")
    private val apiKeyField = JPasswordField()
    private val baseUrlField = JBTextField()
    private val modelField = JBTextField()
    private val quarantineField = JBTextField()
    private val confidenceSpinner = JSpinner(SpinnerNumberModel(0.7, 0.0, 1.0, 0.05))

    override fun getDisplayName(): String = "AI File CleanKeeper"

    override fun createComponent(): JComponent {
        panel = FormBuilder.createFormBuilder()
            .addComponent(enabledBox)
            .addComponent(autoScanBox)
            .addComponent(autoDeleteTmpBox)
            .addComponent(autoDeleteAiBox)
            .addComponent(autoIgnoreBox)
            .addComponent(useAiBox)
            .addComponent(notifyBox)
            .addSeparator()
            .addLabeledComponent("API Key:", apiKeyField)
            .addLabeledComponent("Base URL:", baseUrlField)
            .addLabeledComponent("Model:", modelField)
            .addLabeledComponent("AI 置信度阈值:", confidenceSpinner)
            .addLabeledComponent("隔离目录（相对项目根）:", quarantineField)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        reset()
        return panel!!
    }

    override fun isModified(): Boolean {
        val s = CleanKeeperSettings.getInstance()
        return enabledBox.isSelected != s.enabled
            || autoScanBox.isSelected != s.autoScanOnImport
            || autoDeleteTmpBox.isSelected != s.autoDeleteTmp
            || autoDeleteAiBox.isSelected != s.autoDeleteAiUseless
            || autoIgnoreBox.isSelected != s.autoAddToIgnore
            || useAiBox.isSelected != s.useAiClassification
            || notifyBox.isSelected != s.showNotifications
            || String(apiKeyField.password) != s.apiKey
            || baseUrlField.text != s.baseUrl
            || modelField.text != s.model
            || quarantineField.text != s.quarantineDir
            || confidenceSpinner.value != s.aiConfidenceThreshold
    }

    override fun apply() {
        val s = CleanKeeperSettings.getInstance()
        s.enabled = enabledBox.isSelected
        s.autoScanOnImport = autoScanBox.isSelected
        s.autoDeleteTmp = autoDeleteTmpBox.isSelected
        s.autoDeleteAiUseless = autoDeleteAiBox.isSelected
        s.autoAddToIgnore = autoIgnoreBox.isSelected
        s.useAiClassification = useAiBox.isSelected
        s.showNotifications = notifyBox.isSelected
        s.apiKey = String(apiKeyField.password)
        s.baseUrl = baseUrlField.text.trim().removeSuffix("/")
        s.model = modelField.text.trim()
        s.quarantineDir = quarantineField.text.trim()
        s.aiConfidenceThreshold = confidenceSpinner.value as Double
    }

    override fun reset() {
        val s = CleanKeeperSettings.getInstance()
        enabledBox.isSelected = s.enabled
        autoScanBox.isSelected = s.autoScanOnImport
        autoDeleteTmpBox.isSelected = s.autoDeleteTmp
        autoDeleteAiBox.isSelected = s.autoDeleteAiUseless
        autoIgnoreBox.isSelected = s.autoAddToIgnore
        useAiBox.isSelected = s.useAiClassification
        notifyBox.isSelected = s.showNotifications
        apiKeyField.text = s.apiKey
        baseUrlField.text = s.baseUrl
        modelField.text = s.model
        quarantineField.text = s.quarantineDir
        confidenceSpinner.value = s.aiConfidenceThreshold
    }
}
