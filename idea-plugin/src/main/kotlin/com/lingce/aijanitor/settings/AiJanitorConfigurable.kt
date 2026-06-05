package com.lingce.aijanitor.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel

/**
 * "Settings | Tools | AI File Janitor" configuration page.
 */
class AiJanitorConfigurable : BoundConfigurable("AI File Janitor") {

    private val settings = AiJanitorSettings.getInstance()
    private val apiKeyField = JBPasswordField()
    private val tempGlobsArea = JBTextArea(5, 40)

    override fun createPanel(): DialogPanel {
        apiKeyField.text = settings.apiKey
        tempGlobsArea.text = settings.extraTempGlobs
        return panel {
            group("AI 接口配置 (OpenAI 兼容)") {
                row("Base URL:") {
                    textField()
                        .bindText(settings::baseUrl)
                        .columns(40)
                    comment("例如 https://api.openai.com/v1 / https://api.deepseek.com/v1")
                }
                row("API Key:") {
                    cell(apiKeyField)
                        .columns(40)
                        .onApply { settings.apiKey = String(apiKeyField.password) }
                        .onReset { apiKeyField.text = settings.apiKey }
                        .onIsModified { String(apiKeyField.password) != settings.apiKey }
                    comment("留空则仅使用本地启发式规则，不调用任何外部服务")
                }
                row("Model:") {
                    textField()
                        .bindText(settings::model)
                        .columns(25)
                    comment("例如 gpt-4o-mini / deepseek-chat / qwen-plus")
                }
                row {
                    checkBox("启用 AI 辅助识别")
                        .bindSelected(settings::aiEnabled)
                }
                row("请求超时 (秒):") {
                    intTextField(1..600)
                        .bindIntText(settings::requestTimeoutSeconds)
                        .columns(6)
                }
                row("发送内容上限 (字符):") {
                    intTextField(200..32000)
                        .bindIntText(settings::maxContentChars)
                        .columns(8)
                    comment("仅截取文件开头若干字符用于判定，降低 token 消耗与泄露风险")
                }
            }

            group("清理行为") {
                row {
                    checkBox("导入/新增文件时自动扫描")
                        .bindSelected(settings::autoScanOnImport)
                }
                row {
                    checkBox("高置信度的临时/无用文件直接删除（不再逐个确认）")
                        .bindSelected(settings::autoDeleteConfident)
                }
                row {
                    checkBox("将配置类文件写入 .gitignore")
                        .bindSelected(settings::addToGitIgnore)
                }
                row {
                    checkBox("将配置类目录标记为 IDE Excluded")
                        .bindSelected(settings::markExcluded)
                }
                row("可疑文件转存目录:") {
                    textField()
                        .bindText(settings::quarantineDir)
                        .columns(40)
                    comment("相对路径基于项目根目录，例如 .ai-janitor/quarantine，亦可填绝对路径")
                }
            }

            group("自定义临时文件规则") {
                row {
                    cell(tempGlobsArea)
                        .columns(40)
                        .onApply { settings.extraTempGlobs = tempGlobsArea.text }
                        .onReset { tempGlobsArea.text = settings.extraTempGlobs }
                        .onIsModified { tempGlobsArea.text != settings.extraTempGlobs }
                    comment("每行一个 glob，命中即视为临时文件，例如 *.generated.ts 或 scratch_*")
                }
            }
        }
    }
}
