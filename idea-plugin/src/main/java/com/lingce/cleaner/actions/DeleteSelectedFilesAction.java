package com.lingce.cleaner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.lingce.cleaner.core.AiFileCleanerProjectService;
import org.jetbrains.annotations.NotNull;

public final class DeleteSelectedFilesAction extends AnAction implements DumbAware {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        VirtualFile[] files = SelectedFiles.from(event);
        if (files.length == 0) {
            return;
        }

        int result = Messages.showYesNoDialog(
            project,
            "Delete " + files.length + " selected file(s)? This cannot be undone.",
            "Delete Suspicious File(s)",
            Messages.getWarningIcon()
        );
        if (result != Messages.YES) {
            return;
        }

        AiFileCleanerProjectService service = project.getService(AiFileCleanerProjectService.class);
        for (VirtualFile file : files) {
            service.delete(file);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(event.getProject() != null && SelectedFiles.from(event).length > 0);
    }
}
