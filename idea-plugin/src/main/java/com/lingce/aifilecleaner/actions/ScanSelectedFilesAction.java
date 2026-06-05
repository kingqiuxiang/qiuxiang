package com.lingce.aifilecleaner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.lingce.aifilecleaner.AiFileCleanerProjectService;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class ScanSelectedFilesAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        VirtualFile[] files = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (project == null || project.isDisposed() || files == null || files.length == 0) {
            return;
        }
        project.getService(AiFileCleanerProjectService.class).scanFiles(Arrays.asList(files), "Scan selected files");
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        VirtualFile[] files = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        event.getPresentation().setEnabled(event.getProject() != null && files != null && files.length > 0);
    }
}
