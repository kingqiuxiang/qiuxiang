package com.lingce.cleaner.core;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.lingce.cleaner.settings.AiFileCleanerSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class FileCleanupOperator {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public void delete(Project project, VirtualFile file) {
        if (!isSafeLocalFile(project, file)) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() ->
            WriteCommandAction.runWriteCommandAction(project, () -> {
                try {
                    if (file.isValid()) {
                        file.delete(this);
                    }
                } catch (IOException ignored) {
                }
            })
        );
    }

    public void quarantine(Project project, VirtualFile file) {
        if (!isSafeLocalFile(project, file)) {
            return;
        }

        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) {
            return;
        }
        AiFileCleanerSettings.StateData settings = AiFileCleanerSettings.getInstance().getState();
        String configured = settings == null ? ".ai-file-cleaner/quarantine" : settings.quarantineDirectory;
        Path quarantineRoot = resolveQuarantineRoot(baseDir, configured);
        Path source = Path.of(file.getPath());
        if (source.normalize().startsWith(quarantineRoot.normalize())) {
            return;
        }

        String relative = VfsUtilCore.getRelativePath(file, baseDir, '/');
        Path target = uniqueTarget(quarantineRoot.resolve(relative == null ? file.getName() : relative));

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                Files.createDirectories(target.getParent());
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                LocalFileSystem.getInstance().refreshAndFindFileByPath(source.getParent().toString());
                LocalFileSystem.getInstance().refreshAndFindFileByPath(target.toString());
            } catch (IOException ignored) {
            }
        });
    }

    private boolean isSafeLocalFile(Project project, VirtualFile file) {
        VirtualFile baseDir = project.getBaseDir();
        return baseDir != null
            && file != null
            && file.isValid()
            && file.isInLocalFileSystem()
            && VfsUtilCore.isAncestor(baseDir, file, false);
    }

    private Path resolveQuarantineRoot(VirtualFile baseDir, String configured) {
        if (configured == null || configured.isBlank()) {
            return Path.of(baseDir.getPath(), ".ai-file-cleaner", "quarantine");
        }
        Path configuredPath = Path.of(configured.trim());
        if (configuredPath.isAbsolute()) {
            return configuredPath;
        }
        return Path.of(baseDir.getPath()).resolve(configuredPath);
    }

    private Path uniqueTarget(Path target) {
        if (!Files.exists(target)) {
            return target;
        }
        String fileName = target.getFileName().toString();
        String stamp = STAMP.format(LocalDateTime.now());
        return target.resolveSibling(fileName + "." + stamp);
    }
}
