package com.lingce.cleaner.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "AiFileCleanerSettings", storages = [Storage("ai-file-cleaner.xml")])
class CleanerSettingsState : PersistentStateComponent<CleanerSettingsState.State> {
    data class State(
        var baseUrl: String = "",
        var apiKey: String = "",
        var useApiDetection: Boolean = false,
        var autoDeleteTmpFiles: Boolean = true,
        var autoDeleteAiFiles: Boolean = true,
        var suspiciousArchiveDir: String = ""
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    fun hasUsableApiConfig(): Boolean {
        return state.useApiDetection && state.baseUrl.isNotBlank() && state.apiKey.isNotBlank()
    }

    companion object {
        fun getInstance(): CleanerSettingsState {
            return ApplicationManager.getApplication().getService(CleanerSettingsState::class.java)
        }
    }
}
