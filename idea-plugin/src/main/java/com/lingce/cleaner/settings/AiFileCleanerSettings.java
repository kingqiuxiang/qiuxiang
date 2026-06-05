package com.lingce.cleaner.settings;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.ide.passwordSafe.PasswordSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
    name = "AiFileCleanerSettings",
    storages = @Storage("ai-file-cleaner.xml")
)
@Service(Service.Level.APP)
public final class AiFileCleanerSettings implements PersistentStateComponent<AiFileCleanerSettings.StateData> {
    public static final String CLEANUP_QUARANTINE = "QUARANTINE";
    public static final String CLEANUP_DELETE = "DELETE";
    private static final CredentialAttributes API_KEY_ATTRIBUTES = new CredentialAttributes("AI File Cleaner API Key");

    private StateData state = new StateData();

    public static AiFileCleanerSettings getInstance() {
        return ApplicationManager.getApplication().getService(AiFileCleanerSettings.class);
    }

    @Override
    public @Nullable StateData getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull StateData state) {
        this.state = state;
    }

    public String getApiKey() {
        String password = PasswordSafe.getInstance().getPassword(API_KEY_ATTRIBUTES);
        return password == null ? "" : password;
    }

    public void setApiKey(String apiKey) {
        String normalized = apiKey == null ? "" : apiKey.trim();
        Credentials credentials = normalized.isEmpty() ? null : new Credentials("api-key", normalized);
        PasswordSafe.getInstance().set(API_KEY_ATTRIBUTES, credentials);
    }

    public static final class StateData {
        public boolean enabled = true;
        public String baseUrl = "https://api.openai.com/v1";
        public String model = "gpt-4o-mini";
        public boolean autoDeleteTempFiles = true;
        public boolean autoHandleAiGeneratedFiles = true;
        public String aiGeneratedCleanupMode = CLEANUP_QUARANTINE;
        public boolean addConfigFilesToIgnore = true;
        public boolean markConfigDirectoriesExcluded = true;
        public boolean notifySuspiciousFiles = true;
        public String quarantineDirectory = ".ai-file-cleaner/quarantine";
        public int maxBytesForAiAnalysis = 24576;
    }
}
