package com.lingce.aicleaner;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.APP)
@State(name = "AiCleanerSettings", storages = @Storage("aiCleanerSettings.xml"))
public final class AiCleanerSettingsState implements PersistentStateComponent<AiCleanerSettingsState.State> {
    static final class State {
        public String apiKey = "";
        public String baseUrl = "https://api.openai.com/v1";
        public String model = "gpt-4o-mini";
        public String quarantineDirName = ".ai-cleaner-quarantine";
        public boolean autoQuarantineTempFiles = true;
        public boolean autoQuarantineUselessAiFiles = false;
        public boolean autoHandleConfigFiles = true;
        public boolean useAiClassifier = false;
        public int maxPreviewBytes = 12000;
    }

    private State state = new State();

    static AiCleanerSettingsState getInstance() {
        return ApplicationManager.getApplication().getService(AiCleanerSettingsState.class);
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    String getApiKey() {
        return safe(state.apiKey);
    }

    void setApiKey(String apiKey) {
        state.apiKey = safe(apiKey);
    }

    String getBaseUrl() {
        return safe(state.baseUrl).isBlank() ? "https://api.openai.com/v1" : safe(state.baseUrl);
    }

    void setBaseUrl(String baseUrl) {
        state.baseUrl = trimTrailingSlash(safe(baseUrl));
    }

    String getModel() {
        return safe(state.model).isBlank() ? "gpt-4o-mini" : safe(state.model);
    }

    void setModel(String model) {
        state.model = safe(model);
    }

    String getQuarantineDirName() {
        return safe(state.quarantineDirName).isBlank() ? ".ai-cleaner-quarantine" : safe(state.quarantineDirName);
    }

    void setQuarantineDirName(String quarantineDirName) {
        state.quarantineDirName = safe(quarantineDirName);
    }

    boolean isAutoQuarantineTempFiles() {
        return state.autoQuarantineTempFiles;
    }

    void setAutoQuarantineTempFiles(boolean value) {
        state.autoQuarantineTempFiles = value;
    }

    boolean isAutoQuarantineUselessAiFiles() {
        return state.autoQuarantineUselessAiFiles;
    }

    void setAutoQuarantineUselessAiFiles(boolean value) {
        state.autoQuarantineUselessAiFiles = value;
    }

    boolean isAutoHandleConfigFiles() {
        return state.autoHandleConfigFiles;
    }

    void setAutoHandleConfigFiles(boolean value) {
        state.autoHandleConfigFiles = value;
    }

    boolean isUseAiClassifier() {
        return state.useAiClassifier && !getApiKey().isBlank();
    }

    void setUseAiClassifier(boolean value) {
        state.useAiClassifier = value;
    }

    int getMaxPreviewBytes() {
        return Math.max(1024, state.maxPreviewBytes);
    }

    void setMaxPreviewBytes(int maxPreviewBytes) {
        state.maxPreviewBytes = Math.max(1024, maxPreviewBytes);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimTrailingSlash(String value) {
        String result = safe(value);
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
