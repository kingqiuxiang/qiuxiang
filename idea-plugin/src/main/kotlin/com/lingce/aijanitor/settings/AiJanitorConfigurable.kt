package com.lingce.aijanitor.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class AiJanitorConfigurable : Configurable {

    private val settings = AiJanitorSettings.getInstance()
    private val apiKeyField = JBPasswordField()
    private var dialogPanel: DialogPanel? = null

    override fun getDisplayName(): String = "AI File Janitor"

    override fun createComponent(): JComponent {
        val state = settings.state
        val p = panel {
            group("AI 模型（OpenAI 兼容）") {
                row("Base URL：") {
                    textField().bindText(state::baseUrl).columns(40)
                }
                row("API Key：") {
                    cell(apiKeyField).columns(40)
                }.comment("密钥保存在 IDE 的 PasswordSafe 中，不写入项目文件。")
                row("模型 Model：") {
                    textField().bindText(state::model).columns(24)
                }
                row {
                    checkBox("启用 AI 智能识别（关闭则仅用本地规则）").bindSelected(state::useAi)
                }
                row("每次发送给 AI 的文件数：") {
                    intTextField(1..200).bindIntText(state::aiBatchSize).columns(6)
                }
            }
            group("清理行为") {
                row("可疑文件转存目录：") {
                    textField().bindText(state::archiveDir).columns(40)
                }.comment("相对项目根目录；会自动加入 .gitignore 并标记为 Excluded。")
                row("配置文件移入目录：") {
                    textField().bindText(state::ignoreDir).columns(40)
                }.comment("项目/AI 配置文件会移入此 ignore/exclude 目录。")
                row("额外临时文件名规则：") {
                    textField().bindText(state::extraTempPatterns).columns(40)
                }.comment("逗号分隔，支持 * 和 ? 通配，例如：*.tmp, draft_*.md")
                row {
                    checkBox("打开项目时提示扫描").bindSelected(state::promptOnOpen)
                }
            }
        }
        dialogPanel = p
        apiKeyField.text = settings.apiKey
        return p
    }

    override fun isModified(): Boolean =
        (dialogPanel?.isModified() ?: false) || String(apiKeyField.password) != settings.apiKey

    override fun apply() {
        dialogPanel?.apply()
        settings.apiKey = String(apiKeyField.password)
    }

    override fun reset() {
        dialogPanel?.reset()
        apiKeyField.text = settings.apiKey
    }

    override fun disposeUIResources() {
        dialogPanel = null
    }
}
