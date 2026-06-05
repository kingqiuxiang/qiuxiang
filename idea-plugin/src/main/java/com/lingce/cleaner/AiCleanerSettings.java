package com.lingce.cleaner;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "LingCeAiCleanerSettings", storages = @Storage("lingce-ai-cleaner.xml"))
public final class AiCleanerSettings implements PersistentStateComponent<AiCleanerSettings.StateData> {
    private StateData state = new StateData();

    public static AiCleanerSettings getInstance() {
        return ApplicationManager.getApplication().getService(AiCleanerSettings.class);
    }

    @Override
    public @NotNull StateData getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull StateData state) {
        this.state = state;
    }

    public static final class StateData {
        public boolean enabled = true;
        public boolean scanOnProjectOpen = true;
        public boolean watchNewFiles = true;
        public boolean remoteDetectionEnabled = false;
        public boolean autoDeleteTempFiles = true;
        public boolean autoDeleteDisposableAiFiles = true;
        public boolean autoIgnoreConfigFiles = true;
        public int maxScanFileSizeKb = 512;
        public String baseUrl = "";
        public String apiKey = "";
        public String model = "gpt-4o-mini";
        public String quarantineDirectory = ".ai-cleaner/quarantine";
        public String ignoreFileName = ".gitignore";
        public String customTempPatterns = "";
    }

    static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}
