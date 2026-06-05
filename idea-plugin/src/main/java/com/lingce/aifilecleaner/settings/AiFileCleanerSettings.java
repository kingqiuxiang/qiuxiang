package com.lingce.aifilecleaner.settings;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.APP)
@State(name = "LingCeAiFileCleanerSettings", storages = @Storage("lingceAiFileCleaner.xml"))
public final class AiFileCleanerSettings implements PersistentStateComponent<AiFileCleanerSettings.PluginState> {
    private static final String API_KEY_SERVICE = "LingCe AI File Cleaner OpenAI Compatible API Key";

    private PluginState state = new PluginState();

    public static AiFileCleanerSettings getInstance() {
        return ApplicationManager.getApplication().getService(AiFileCleanerSettings.class);
    }

    @Override
    public @NotNull PluginState getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull PluginState state) {
        this.state = state;
    }

    public @Nullable String getApiKey() {
        return PasswordSafe.getInstance().getPassword(new CredentialAttributes(API_KEY_SERVICE));
    }

    public void setApiKey(@Nullable String apiKey) {
        String normalized = apiKey == null || apiKey.isBlank() ? null : apiKey.trim();
        PasswordSafe.getInstance().setPassword(new CredentialAttributes(API_KEY_SERVICE), normalized);
    }

    public static final class PluginState {
        public String baseUrl = "https://api.openai.com/v1";
        public String model = "gpt-4o-mini";
        public String quarantineDirectory = ".ai-file-cleaner/quarantine";
        public int maxSampleBytes = 12000;
        public boolean useAiClassifier = true;
        public boolean autoCleanTemporaryFiles = true;
        public boolean autoDeleteObviousAiTrash = false;
        public boolean autoIgnoreConfigFiles = true;
        public boolean scanOnProjectOpen = true;
        public boolean scanNewFiles = true;
    }
}
