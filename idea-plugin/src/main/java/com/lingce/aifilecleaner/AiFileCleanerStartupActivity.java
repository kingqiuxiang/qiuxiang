package com.lingce.aifilecleaner;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

public final class AiFileCleanerStartupActivity implements StartupActivity.DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
        project.getService(AiFileCleanerProjectService.class).start();
    }
}
