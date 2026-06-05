package io.lingce.ideaguardian.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

data class AiFileGuardianSettingsState(
    var apiKey: String = "",
    var baseUrl: String = "https://api.openai.com/v1",
    var archiveDirectory: String = "",
    var maxApiDetectionsPerScan: Int = 20
)

@Service(Service.Level.APP)
@State(name = "AiFileGuardianSettings", storages = [Storage("ai-file-guardian.xml")])
class AiFileGuardianSettingsService : PersistentStateComponent<AiFileGuardianSettingsState> {
    private var state: AiFileGuardianSettingsState = AiFileGuardianSettingsState()

    override fun getState(): AiFileGuardianSettingsState = state

    override fun loadState(state: AiFileGuardianSettingsState) {
        this.state = state
    }
}
