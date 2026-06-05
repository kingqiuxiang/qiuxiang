package com.lingce.cleanguard.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "CleanGuardSettings", storages = [Storage("clean-guard.xml")])
class CleanGuardSettings : PersistentStateComponent<CleanGuardSettings.State> {

    data class State(
        var enabled: Boolean = true,
        var apiKey: String = "",
        var baseUrl: String = "https://api.openai.com/v1",
        var model: String = "gpt-4o-mini",
        var quarantineDir: String = ".clean-guard/quarantine",
        var autoCleanTmp: Boolean = true,
        var autoCleanAiGenerated: Boolean = true,
        var autoExcludeConfig: Boolean = true,
        var useAiDetection: Boolean = true,
        var notifyOnAction: Boolean = true,
        var maxAiFileBytes: Int = 32_768,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(loaded: State) {
        XmlSerializerUtil.copyBean(loaded, state)
    }

    companion object {
        fun getInstance(): CleanGuardSettings =
            ApplicationManager.getApplication().getService(CleanGuardSettings::class.java)
    }
}
