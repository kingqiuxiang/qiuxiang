package com.aifileguard.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class AiGuardConfigurable : Configurable {

    private val baseUrl = JBTextField()
    private val apiKey = JBPasswordField()
    private val model = JBTextField()
    private val enableAi = JBCheckBox("Use AI to classify unknown/suspicious files")
    private val scanOnOpen = JBCheckBox("Scan automatically when a project is opened/imported")
    private val watchNewFiles = JBCheckBox("Flag newly created/imported files in real time")
    private val autoApplySafe = JBCheckBox("Auto-apply high-confidence safe actions")
    private val quarantineDir = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            "Quarantine Directory",
            "Suspicious files will be moved here",
            null,
            FileChooserDescriptorFactory.createSingleFolderDescriptor(),
        )
    }
    private val maxAiFileSizeKb = JBTextField()

    private val tmpPatterns = JBTextArea(5, 40)
    private val aiArtifactPatterns = JBTextArea(5, 40)
    private val projectConfigPatterns = JBTextArea(5, 40)
    private val aiConfigPatterns = JBTextArea(5, 40)

    private var root: JPanel? = null

    override fun getDisplayName(): String = "AI File Guard"

    override fun createComponent(): JComponent {
        val panel = FormBuilder.createFormBuilder()
            .addComponent(JBLabel("<html><b>AI connection (OpenAI-compatible)</b></html>"))
            .addLabeledComponent("Base URL:", baseUrl)
            .addLabeledComponent("API Key:", apiKey)
            .addLabeledComponent("Model:", model)
            .addComponent(enableAi)
            .addSeparator()
            .addComponent(JBLabel("<html><b>Behaviour</b></html>"))
            .addComponent(scanOnOpen)
            .addComponent(watchNewFiles)
            .addComponent(autoApplySafe)
            .addLabeledComponent("Quarantine directory:", quarantineDir)
            .addLabeledComponent("Max AI file size (KB):", maxAiFileSizeKb)
            .addSeparator()
            .addComponent(JBLabel("<html><b>Classification patterns</b> (one per line; supports * ? **)</html>"))
            .addLabeledComponent("Temporary files:", JBScrollPane(tmpPatterns))
            .addLabeledComponent("AI-generated artifacts:", JBScrollPane(aiArtifactPatterns))
            .addLabeledComponent("Project config:", JBScrollPane(projectConfigPatterns))
            .addLabeledComponent("AI config:", JBScrollPane(aiConfigPatterns))
            .addComponentFillVertically(JPanel(), 0)
            .panel
        root = panel
        reset()
        return panel
    }

    private fun state() = AiGuardSettings.getInstance().state

    override fun isModified(): Boolean {
        val s = state()
        return baseUrl.text != s.baseUrl ||
            String(apiKey.password) != s.apiKey ||
            model.text != s.model ||
            enableAi.isSelected != s.enableAi ||
            scanOnOpen.isSelected != s.scanOnOpen ||
            watchNewFiles.isSelected != s.watchNewFiles ||
            autoApplySafe.isSelected != s.autoApplySafe ||
            quarantineDir.text != s.quarantineDir ||
            maxAiFileSizeKb.text != s.maxAiFileSizeKb.toString() ||
            tmpPatterns.text != s.tmpPatterns ||
            aiArtifactPatterns.text != s.aiArtifactPatterns ||
            projectConfigPatterns.text != s.projectConfigPatterns ||
            aiConfigPatterns.text != s.aiConfigPatterns
    }

    override fun apply() {
        val s = state()
        s.baseUrl = baseUrl.text.trim()
        s.apiKey = String(apiKey.password)
        s.model = model.text.trim()
        s.enableAi = enableAi.isSelected
        s.scanOnOpen = scanOnOpen.isSelected
        s.watchNewFiles = watchNewFiles.isSelected
        s.autoApplySafe = autoApplySafe.isSelected
        s.quarantineDir = quarantineDir.text.trim()
        s.maxAiFileSizeKb = maxAiFileSizeKb.text.trim().toIntOrNull()?.coerceIn(1, 10240) ?: s.maxAiFileSizeKb
        s.tmpPatterns = tmpPatterns.text
        s.aiArtifactPatterns = aiArtifactPatterns.text
        s.projectConfigPatterns = projectConfigPatterns.text
        s.aiConfigPatterns = aiConfigPatterns.text
    }

    override fun reset() {
        val s = state()
        baseUrl.text = s.baseUrl
        apiKey.text = s.apiKey
        model.text = s.model
        enableAi.isSelected = s.enableAi
        scanOnOpen.isSelected = s.scanOnOpen
        watchNewFiles.isSelected = s.watchNewFiles
        autoApplySafe.isSelected = s.autoApplySafe
        quarantineDir.text = s.quarantineDir
        maxAiFileSizeKb.text = s.maxAiFileSizeKb.toString()
        tmpPatterns.text = s.tmpPatterns
        aiArtifactPatterns.text = s.aiArtifactPatterns
        projectConfigPatterns.text = s.projectConfigPatterns
        aiConfigPatterns.text = s.aiConfigPatterns
    }
}
