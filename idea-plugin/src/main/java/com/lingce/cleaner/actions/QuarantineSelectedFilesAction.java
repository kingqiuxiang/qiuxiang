package com.lingce.cleaner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.lingce.cleaner.core.AiFileCleanerProjectService;
import org.jetbrains.annotations.NotNull;

public final class QuarantineSelectedFilesAction extends AnAction implements DumbAware {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        AiFileCleanerProjectService service = project.getService(AiFileCleanerProjectService.class);
        for (VirtualFile file : SelectedFiles.from(event)) {
            service.quarantine(file);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(event.getProject() != null && SelectedFiles.from(event).length > 0);
    }
}
