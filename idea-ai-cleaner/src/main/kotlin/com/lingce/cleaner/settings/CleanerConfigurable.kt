package com.lingce.cleaner.settings

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class CleanerConfigurable : Configurable {
    private val settings = CleanerSettingsState.getInstance()

    private val baseUrlField = JBTextField()
    private val apiKeyField = JBPasswordField()
    private val useApiDetectionCheck = JBCheckBox("启用 API 辅助识别 AI 文件")
    private val autoDeleteTmpCheck = JBCheckBox("自动删除 tmp/temp/临时文件")
    private val autoDeleteAiCheck = JBCheckBox("自动删除识别出的 AI 生成无用文件")
    private val archivePathField = JBTextField()

    private var rootPanel: JPanel? = null

    override fun getDisplayName(): String = "AI File Cleaner"

    override fun createComponent(): JComponent {
        val browseButton = JButton("选择目录")
        browseButton.addActionListener {
            val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            val chosen = FileChooser.chooseFile(descriptor, null, null)
            if (chosen != null) {
                archivePathField.text = chosen.path
            }
        }

        val archivePanel = JPanel(BorderLayout(8, 0))
        archivePanel.add(archivePathField, BorderLayout.CENTER)
        archivePanel.add(browseButton, BorderLayout.EAST)

        rootPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JLabel("Base URL (OpenAI兼容):"), baseUrlField, 1, false)
            .addLabeledComponent(JLabel("API Key:"), apiKeyField, 1, false)
            .addComponent(useApiDetectionCheck)
            .addComponent(autoDeleteTmpCheck)
            .addComponent(autoDeleteAiCheck)
            .addLabeledComponent(JLabel("可疑文件一键转存目录:"), archivePanel, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        reset()
        return rootPanel as JPanel
    }

    override fun isModified(): Boolean {
        val state = settings.state
        return baseUrlField.text != state.baseUrl ||
            String(apiKeyField.password) != state.apiKey ||
            useApiDetectionCheck.isSelected != state.useApiDetection ||
            autoDeleteTmpCheck.isSelected != state.autoDeleteTmpFiles ||
            autoDeleteAiCheck.isSelected != state.autoDeleteAiFiles ||
            archivePathField.text != state.suspiciousArchiveDir
    }

    override fun apply() {
        val baseUrl = baseUrlField.text.trim()
        if (baseUrl.isNotBlank() && !baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            Messages.showErrorDialog("Base URL 必须以 http:// 或 https:// 开头。", "AI File Cleaner")
            return
        }

        val state = settings.state
        state.baseUrl = baseUrl
        state.apiKey = String(apiKeyField.password).trim()
        state.useApiDetection = useApiDetectionCheck.isSelected
        state.autoDeleteTmpFiles = autoDeleteTmpCheck.isSelected
        state.autoDeleteAiFiles = autoDeleteAiCheck.isSelected
        state.suspiciousArchiveDir = archivePathField.text.trim()
    }

    override fun reset() {
        val state = settings.state
        baseUrlField.text = state.baseUrl
        apiKeyField.text = state.apiKey
        useApiDetectionCheck.isSelected = state.useApiDetection
        autoDeleteTmpCheck.isSelected = state.autoDeleteTmpFiles
        autoDeleteAiCheck.isSelected = state.autoDeleteAiFiles
        archivePathField.text = state.suspiciousArchiveDir
    }

    override fun disposeUIResources() {
        rootPanel = null
    }
}
