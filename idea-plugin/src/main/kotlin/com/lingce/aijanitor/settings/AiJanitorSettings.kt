package com.lingce.aijanitor.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Application-wide settings for the AI File Janitor plugin.
 *
 * The API key is stored securely in the IDE [PasswordSafe]; everything else is
 * persisted as plain XML.
 */
@State(name = "AiJanitorSettings", storages = [Storage("aiFileJanitor.xml")])
class AiJanitorSettings : PersistentStateComponent<AiJanitorSettings.State> {

    data class State(
        var baseUrl: String = "https://api.openai.com/v1",
        var model: String = "gpt-4o-mini",
        /** Directory (relative to project root) used for "archive / 转存". */
        var archiveDir: String = ".ai-archive/quarantine",
        /** Directory (relative to project root) — legacy, kept for backward compat. */
        var ignoreDir: String = ".ai-archive/ignored",
        /** Whether to call the AI model to refine the heuristic classification. */
        var useAi: Boolean = true,
        /** Show a notification suggesting a scan when a project is opened. */
        var promptOnOpen: Boolean = true,
        /** Maximum number of files sent to the AI per request. */
        var aiBatchSize: Int = 30,
        /** Extra glob-ish name patterns (comma separated) treated as temp/junk. */
        var extraTempPatterns: String = "",
        /** Patterns (comma separated) for files that AI tools need — shown as AI_CONFIG, not cleaned. */
        var aiKeepPatterns: String = "",
        /** Glob patterns (comma separated) for files to permanently skip during scan. */
        var permanentIgnorePatterns: String = "",
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(newState: State) {
        this.state = newState
    }

    private val credentialAttributes: CredentialAttributes
        get() = CredentialAttributes(generateServiceName("AiFileJanitor", "apiKey"))

    var apiKey: String
        get() = PasswordSafe.instance.getPassword(credentialAttributes).orEmpty()
        set(value) {
            PasswordSafe.instance.setPassword(credentialAttributes, value.ifBlank { null })
        }

    /** True when an API key and base url are configured so AI calls are possible. */
    fun aiConfigured(): Boolean = state.useAi && apiKey.isNotBlank() && state.baseUrl.isNotBlank()

    companion object {
        fun getInstance(): AiJanitorSettings =
            ApplicationManager.getApplication().getService(AiJanitorSettings::class.java)
    }
}
