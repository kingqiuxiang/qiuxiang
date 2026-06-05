package com.lingce.aijanitor.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Application-level persistent settings for the AI File Janitor plugin.
 *
 * The API key is stored together with the rest of the configuration for
 * simplicity. It never leaves the machine except for requests the user
 * explicitly triggers against the configured [baseUrl].
 */
@Service(Service.Level.APP)
@State(
    name = "AiFileJanitorSettings",
    storages = [Storage("aiFileJanitor.xml")],
)
class AiJanitorSettings : PersistentStateComponent<AiJanitorSettings> {

    /** OpenAI-compatible base url, e.g. https://api.openai.com/v1 */
    var baseUrl: String = "https://api.openai.com/v1"

    /** API key for the OpenAI-compatible endpoint. Empty => heuristic-only mode. */
    var apiKey: String = ""

    /** Model name, e.g. gpt-4o-mini / deepseek-chat / qwen-plus */
    var model: String = "gpt-4o-mini"

    /** Whether to call the AI endpoint at all. When false, only heuristics run. */
    var aiEnabled: Boolean = true

    /** Directory (absolute or project-relative) used to quarantine suspicious files. */
    var quarantineDir: String = ".ai-janitor/quarantine"

    /** Automatically scan newly added/imported files. */
    var autoScanOnImport: Boolean = true

    /**
     * When true, files classified with a confident "delete" recommendation
     * (temp / useless AI output) are deleted automatically without asking.
     * When false, the user reviews everything in the result dialog first.
     */
    var autoDeleteConfident: Boolean = false

    /** Add config files to .gitignore (VCS ignore) when classified as IGNORE. */
    var addToGitIgnore: Boolean = true

    /** Mark config files/dirs as "excluded" in the IDE when classified as IGNORE. */
    var markExcluded: Boolean = true

    /** Maximum number of characters of file content sent to the AI for classification. */
    var maxContentChars: Int = 4000

    /** Request timeout (seconds) for AI classification calls. */
    var requestTimeoutSeconds: Int = 30

    /** User-defined extra glob patterns (one per line) treated as temp/junk. */
    var extraTempGlobs: String = ""

    override fun getState(): AiJanitorSettings = this

    override fun loadState(state: AiJanitorSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    fun isAiConfigured(): Boolean = aiEnabled && apiKey.isNotBlank() && baseUrl.isNotBlank()

    companion object {
        @JvmStatic
        fun getInstance(): AiJanitorSettings =
            ApplicationManager.getApplication().getService(AiJanitorSettings::class.java)
    }
}
