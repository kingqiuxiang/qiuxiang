package com.lingce.cleaner.core;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtilCore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class IgnoreAndExcludeManager {
    public void addToGitignore(Project project, VirtualFile file) {
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null || !file.isInLocalFileSystem()) {
            return;
        }

        String relativePath = VfsUtilCore.getRelativePath(file, baseDir, '/');
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        String pattern = file.isDirectory() && !relativePath.endsWith("/") ? relativePath + "/" : relativePath;
        Path gitignore = Path.of(baseDir.getPath(), ".gitignore");

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                String existing = Files.exists(gitignore) ? Files.readString(gitignore, StandardCharsets.UTF_8) : "";
                if (containsPattern(existing, pattern)) {
                    return;
                }
                String prefix = existing.endsWith("\n") || existing.isEmpty() ? "" : "\n";
                Files.writeString(
                    gitignore,
                    existing + prefix + "# Added by AI File Cleaner\n" + pattern + "\n",
                    StandardCharsets.UTF_8
                );
                LocalFileSystem.getInstance().refreshAndFindFileByPath(gitignore.toString());
            } catch (IOException ignored) {
            }
        });
    }

    public void markExcluded(Project project, VirtualFile file) {
        VirtualFile target = file.isDirectory() ? file : file.getParent();
        if (target == null || !target.isInLocalFileSystem()) {
            return;
        }

        Module module = ModuleUtilCore.findModuleForFile(target, project);
        if (module == null) {
            return;
        }

        WriteCommandAction.runWriteCommandAction(project, () -> {
            ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
            boolean changed = false;
            try {
                for (ContentEntry entry : model.getContentEntries()) {
                    VirtualFile contentRoot = entry.getFile();
                    if (contentRoot != null && VfsUtilCore.isAncestor(contentRoot, target, false)) {
                        entry.addExcludeFolder(target);
                        changed = true;
                        break;
                    }
                }
                if (changed) {
                    model.commit();
                } else {
                    model.dispose();
                }
            } catch (RuntimeException ex) {
                model.dispose();
            }
        });
    }

    private boolean containsPattern(String existing, String pattern) {
        String normalized = "\n" + existing.replace("\r\n", "\n") + "\n";
        return normalized.contains("\n" + pattern + "\n");
    }
}
