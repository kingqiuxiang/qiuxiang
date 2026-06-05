package com.lingce.cleankeeper.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "CleanKeeperSettings", storages = [Storage("ai-file-cleankeeper.xml")])
class CleanKeeperSettings : PersistentStateComponent<CleanKeeperSettings> {

    var apiKey: String = ""
    var baseUrl: String = "https://api.openai.com/v1"
    var model: String = "gpt-4o-mini"
    var enabled: Boolean = true
    var autoScanOnImport: Boolean = true
    var autoDeleteTmp: Boolean = true
    var autoDeleteAiUseless: Boolean = true
    var autoAddToIgnore: Boolean = true
    var useAiClassification: Boolean = true
    var quarantineDir: String = ".cleankeeper/quarantine"
    var customIgnorePatterns: MutableList<String> = mutableListOf()
    var customTmpPatterns: MutableList<String> = mutableListOf()
    var customAiConfigPatterns: MutableList<String> = mutableListOf()
    var showNotifications: Boolean = true
    var aiConfidenceThreshold: Double = 0.7

    override fun getState(): CleanKeeperSettings = this

    override fun loadState(state: CleanKeeperSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    fun isAiConfigured(): Boolean = apiKey.isNotBlank() && baseUrl.isNotBlank()

    companion object {
        fun getInstance(): CleanKeeperSettings =
            ApplicationManager.getApplication().getService(CleanKeeperSettings::class.java)
    }
}
