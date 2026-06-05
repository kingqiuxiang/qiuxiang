package com.lingce.aicleaner.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.lingce.aicleaner.core.AiClient
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets

/**
 * 设置页：配置 AI 接口（baseUrl / apiKey / model）、行为开关与各类清理规则。
 */
class AiCleanerConfigurable : Configurable {

    private val settings = AiCleanerSettings.getInstance()

    private val baseUrlField = JBTextField()
    private val apiKeyField = JBPasswordField()
    private val modelField = JBTextField()
    private val quarantineField = JBTextField()

    private val enableAiBox = JCheckBox("启用 AI 辅助判定（本地规则不确定时调用）")
    private val watchBox = JCheckBox("监听导入/新建文件并自动分类提示")
    private val autoCleanBox = JCheckBox("自动执行清理（可疑文件永远不会被自动删除）")
    private val deleteToQuarantineBox = JCheckBox("删除前先转存到隔离目录（更安全）")

    private val tmpArea = JBTextArea(3, 60)
    private val aiConfigArea = JBTextArea(3, 60)
    private val projectConfigArea = JBTextArea(3, 60)
    private val aiGenNameArea = JBTextArea(3, 60)
    private val markersArea = JBTextArea(4, 60)

    override fun getDisplayName(): String = "AI 文件清理 (AI File Cleaner)"

    override fun createComponent(): JComponent {
        val panel = JPanel(GridBagLayout())
        val c = GridBagConstraints().apply {
            gridx = 0; gridy = 0
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            insets = Insets(4, 8, 4, 8)
            gridwidth = 2
        }

        fun add(comp: JComponent) {
            panel.add(comp, c); c.gridy++
        }

        fun section(title: String) {
            val l = JLabel("<html><b>$title</b></html>")
            l.border = JBUI.Borders.emptyTop(10)
            add(l)
        }

        section("AI 接口配置")
        add(labeled("Base URL：", baseUrlField))
        add(labeled("API Key：", apiKeyField))
        add(labeled("Model：", modelField))
        val testBtn = javax.swing.JButton("测试连接")
        testBtn.addActionListener { testConnection() }
        add(testBtn)

        section("行为")
        add(enableAiBox)
        add(watchBox)
        add(autoCleanBox)
        add(deleteToQuarantineBox)
        add(labeled("隔离目录(留空=项目根/.ai-cleaner-quarantine)：", quarantineField))

        section("规则：临时文件（删除）")
        add(scroll(tmpArea))
        section("规则：AI 配置文件（加入忽略/排除）")
        add(scroll(aiConfigArea))
        section("规则：项目配置文件（加入忽略/排除）")
        add(scroll(projectConfigArea))
        section("规则：AI 生成无用文件 - 文件名（删除）")
        add(scroll(aiGenNameArea))
        section("规则：AI 生成标记 - 内容关键字，每行一个（删除）")
        add(scroll(markersArea))

        // 撑满底部
        c.weighty = 1.0; c.fill = GridBagConstraints.BOTH
        panel.add(JPanel(), c)

        reset()
        return panel
    }

    private fun labeled(label: String, field: JComponent): JPanel {
        val p = JPanel(GridBagLayout())
        val lc = GridBagConstraints().apply {
            gridx = 0; gridy = 0; anchor = GridBagConstraints.WEST
            insets = Insets(0, 0, 0, 8)
        }
        val l = JLabel(label)
        l.preferredSize = java.awt.Dimension(260, l.preferredSize.height)
        p.add(l, lc)
        lc.gridx = 1; lc.weightx = 1.0; lc.fill = GridBagConstraints.HORIZONTAL
        p.add(field, lc)
        return p
    }

    private fun scroll(area: JBTextArea): JComponent {
        area.lineWrap = true
        area.wrapStyleWord = true
        return com.intellij.ui.components.JBScrollPane(area)
    }

    private fun testConnection() {
        // 先把当前界面输入写入设置，便于测试
        apply()
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            {
                val msg = AiClient(settings).testConnection()
                ApplicationManager.getApplication().invokeLater {
                    Messages.showInfoMessage(msg, "AI 连接测试")
                }
            },
            "测试 AI 连接…", true, null,
        )
    }

    override fun isModified(): Boolean {
        val s = settings.state
        return baseUrlField.text != s.baseUrl ||
            String(apiKeyField.password) != settings.apiKey ||
            modelField.text != s.model ||
            quarantineField.text != s.quarantineDir ||
            enableAiBox.isSelected != s.enableAi ||
            watchBox.isSelected != s.watchImportedFiles ||
            autoCleanBox.isSelected != s.autoClean ||
            deleteToQuarantineBox.isSelected != s.deleteToQuarantine ||
            tmpArea.text != s.tmpPatterns ||
            aiConfigArea.text != s.aiConfigPatterns ||
            projectConfigArea.text != s.projectConfigPatterns ||
            aiGenNameArea.text != s.aiGeneratedNamePatterns ||
            markersArea.text != s.aiContentMarkers
    }

    override fun apply() {
        val s = settings.state
        s.baseUrl = baseUrlField.text.trim()
        settings.apiKey = String(apiKeyField.password)
        s.model = modelField.text.trim()
        s.quarantineDir = quarantineField.text.trim()
        s.enableAi = enableAiBox.isSelected
        s.watchImportedFiles = watchBox.isSelected
        s.autoClean = autoCleanBox.isSelected
        s.deleteToQuarantine = deleteToQuarantineBox.isSelected
        s.tmpPatterns = tmpArea.text
        s.aiConfigPatterns = aiConfigArea.text
        s.projectConfigPatterns = projectConfigArea.text
        s.aiGeneratedNamePatterns = aiGenNameArea.text
        s.aiContentMarkers = markersArea.text
    }

    override fun reset() {
        val s = settings.state
        baseUrlField.text = s.baseUrl
        apiKeyField.text = settings.apiKey
        modelField.text = s.model
        quarantineField.text = s.quarantineDir
        enableAiBox.isSelected = s.enableAi
        watchBox.isSelected = s.watchImportedFiles
        autoCleanBox.isSelected = s.autoClean
        deleteToQuarantineBox.isSelected = s.deleteToQuarantine
        tmpArea.text = s.tmpPatterns
        aiConfigArea.text = s.aiConfigPatterns
        projectConfigArea.text = s.projectConfigPatterns
        aiGenNameArea.text = s.aiGeneratedNamePatterns
        markersArea.text = s.aiContentMarkers
    }
}
