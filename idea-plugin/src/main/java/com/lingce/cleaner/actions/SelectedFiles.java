package com.lingce.cleaner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.vfs.VirtualFile;

public final class SelectedFiles {
    private SelectedFiles() {
    }

    public static VirtualFile[] from(AnActionEvent event) {
        VirtualFile[] files = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        return files == null ? new VirtualFile[0] : files;
    }
}
