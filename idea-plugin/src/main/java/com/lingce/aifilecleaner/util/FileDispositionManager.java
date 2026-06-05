package com.lingce.aifilecleaner.util;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.lingce.aifilecleaner.classifier.FileClassification;
import com.lingce.aifilecleaner.settings.AiFileCleanerSettings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;

public final class FileDispositionManager {
    private final Project project;

    public FileDispositionManager(Project project) {
        this.project = project;
    }

    public void delete(VirtualFile file) {
        if (file == null || !file.isValid()) {
            return;
        }
        WriteCommandAction.runWriteCommandAction(project, "Delete " + file.getName(), null, () -> {
            try {
                file.delete(this);
            } catch (IOException error) {
                notifyError("Could not delete " + file.getPath(), error);
            }
        });
    }

    public void quarantine(VirtualFile file) {
        if (file == null || !file.isValid() || !file.isInLocalFileSystem()) {
            return;
        }
        Path source = VfsUtilCore.virtualToIoFile(file).toPath();
        Path target = quarantineTarget(file);
        WriteCommandAction.runWriteCommandAction(project, "Move to AI Cleaner Quarantine", null, () -> {
            try {
                Files.createDirectories(target.getParent());
                Files.move(source, uniqueTarget(target), StandardCopyOption.REPLACE_EXISTING);
                VirtualFileManager.getInstance().syncRefresh();
            } catch (IOException error) {
                notifyError("Could not move " + file.getPath() + " to quarantine", error);
            }
        });
    }

    public void ignoreOrExclude(VirtualFile file, FileClassification classification) {
        if (file == null || !file.isValid()) {
            return;
        }
        addToGitignore(file);
        if (file.isDirectory()) {
            excludeDirectory(file);
        }
        NotificationGroupManager.getInstance()
                .getNotificationGroup("AI File Cleaner")
                .createNotification(
                        "Handled " + file.getName(),
                        "Added to .gitignore" + (file.isDirectory() ? " and excluded directory from the IDE index." : ".")
                                + " Reason: " + classification.reason(),
                        NotificationType.INFORMATION
                )
                .notify(project);
    }

    private Path quarantineTarget(VirtualFile file) {
        String configured = AiFileCleanerSettings.getInstance().getState().quarantineDirectory;
        Path base = Path.of(configured);
        if (!base.isAbsolute()) {
            String projectBase = project.getBasePath();
            base = projectBase == null ? base.toAbsolutePath() : Path.of(projectBase).resolve(base);
        }
        return base.resolve(file.getName());
    }

    private static Path uniqueTarget(Path desired) {
        if (!Files.exists(desired)) {
            return desired;
        }
        String fileName = desired.getFileName().toString();
        String stem = fileName;
        String extension = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            stem = fileName.substring(0, dot);
            extension = fileName.substring(dot);
        }
        for (int index = 1; index < 1000; index++) {
            Path candidate = desired.resolveSibling(stem + "-" + index + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        return desired.resolveSibling(stem + "-" + System.currentTimeMillis() + extension);
    }

    private void addToGitignore(VirtualFile file) {
        String projectBase = project.getBasePath();
        VirtualFile baseDir = project.getBaseDir();
        if (projectBase == null || baseDir == null || !file.isInLocalFileSystem()) {
            return;
        }
        String relative = VfsUtilCore.getRelativePath(file, baseDir, '/');
        if (relative == null || relative.isBlank()) {
            return;
        }
        if (file.isDirectory() && !relative.endsWith("/")) {
            relative = relative + "/";
        }

        Path gitignore = Path.of(projectBase).resolve(".gitignore");
        String entry = relative;
        try {
            List<String> existing = Files.exists(gitignore)
                    ? Files.readAllLines(gitignore, StandardCharsets.UTF_8)
                    : List.of();
            if (existing.stream().map(String::trim).anyMatch(entry::equals)) {
                return;
            }
            String prefix = existing.isEmpty() || existing.get(existing.size() - 1).isBlank() ? "" : System.lineSeparator();
            Files.writeString(
                    gitignore,
                    prefix + "# Added by LingCe AI File Cleaner" + System.lineSeparator() + entry + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
            VirtualFileManager.getInstance().syncRefresh();
        } catch (IOException error) {
            notifyError("Could not update .gitignore", error);
        }
    }

    private void excludeDirectory(VirtualFile directory) {
        Module module = ModuleUtilCore.findModuleForFile(directory, project);
        if (module == null) {
            return;
        }
        ModuleRootModificationUtil.updateModel(module, model -> {
            for (ContentEntry entry : model.getContentEntries()) {
                VirtualFile contentRoot = entry.getFile();
                if (contentRoot != null && VfsUtilCore.isAncestor(contentRoot, directory, false)) {
                    boolean alreadyExcluded = false;
                    for (VirtualFile excluded : ModuleRootManager.getInstance(module).getExcludeRoots()) {
                        if (excluded.equals(directory)) {
                            alreadyExcluded = true;
                            break;
                        }
                    }
                    if (!alreadyExcluded) {
                        entry.addExcludeFolder(directory);
                    }
                }
            }
        });
    }

    private void notifyError(String title, Exception error) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("AI File Cleaner")
                .createNotification(title, error.getMessage() == null ? error.toString() : error.getMessage(), NotificationType.ERROR)
                .notify(project);
    }
}
