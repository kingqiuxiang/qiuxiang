package com.lingce.aifilecleaner.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "AiFileCleanerSettings", storages = [Storage("AiFileCleanerSettings.xml")])
class CleanerSettingsState : PersistentStateComponent<CleanerSettingsState.State> {
    data class State(
        var apiKey: String = "",
        var baseUrl: String = "",
        var ignoreExcludeDir: String = ".project-cleaner/ignore-exclude",
        var suspiciousArchiveDir: String = ".project-cleaner/suspicious-archive",
        var autoScanOnStartup: Boolean = true,
        var autoDeleteTmpFiles: Boolean = true,
        var autoDeleteAiJunkFiles: Boolean = true,
        var maxFileSizeKb: Int = 256
    )

    private var myState: State = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        this.myState = state
    }
}
