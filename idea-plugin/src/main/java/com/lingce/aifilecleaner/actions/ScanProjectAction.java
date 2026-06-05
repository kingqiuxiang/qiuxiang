package com.lingce.aifilecleaner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.lingce.aifilecleaner.AiFileCleanerProjectService;
import org.jetbrains.annotations.NotNull;

public final class ScanProjectAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null || project.isDisposed()) {
            return;
        }
        project.getService(AiFileCleanerProjectService.class).scanProject();
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(event.getProject() != null);
    }
}
