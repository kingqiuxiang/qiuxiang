package com.lingce.cleanguardian

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "CleanGuardianSettings", storages = [Storage("clean-guardian.xml")])
class CleanGuardianSettingsService : PersistentStateComponent<CleanGuardianSettingsService.State> {

    data class State(
        var apiBaseUrl: String = "",
        var apiKey: String = "",
        var model: String = "gpt-4o-mini",
        var quarantineDirectory: String = System.getProperty("user.home") + "/.clean-guardian-quarantine",
        var autoDeleteTmpFiles: Boolean = true,
        var autoDeleteAiGarbageFiles: Boolean = true,
        var scanOnProjectOpen: Boolean = true,
        var enableRealtimeMonitor: Boolean = true,
        var maxFileSizeKb: Int = 256
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    companion object {
        fun getInstance(): CleanGuardianSettingsService {
            return ApplicationManager.getApplication().getService(CleanGuardianSettingsService::class.java)
        }
    }
}
