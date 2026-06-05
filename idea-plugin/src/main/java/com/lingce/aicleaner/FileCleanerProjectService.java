package com.lingce.aicleaner;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

@Service(Service.Level.PROJECT)
public final class FileCleanerProjectService implements Disposable {
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of(
            ".git", "node_modules", ".idea", ".gradle", ".ai-cleaner-quarantine"
    );

    private final Project project;
    private final FileClassifier classifier = new FileClassifier();
    private final List<SuspectFileRecord> suspectRecords = new CopyOnWriteArrayList<>();
    private final Set<String> pendingPaths = Collections.synchronizedSet(new LinkedHashSet<>());

    public FileCleanerProjectService(Project project) {
        this.project = project;
    }

    public void startWatching() {
        VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileListener() {
            @Override
            public void fileCreated(@NotNull VirtualFileEvent event) {
                scheduleInspection(event.getFile(), false);
            }

            @Override
            public void contentsChanged(@NotNull VirtualFileEvent event) {
                scheduleInspection(event.getFile(), false);
            }
        }, this);
    }

    public void inspectFile(VirtualFile file, boolean interactive) {
        if (!isCandidate(file)) {
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String preview = readPreview(file);
            ClassificationResult result = classifier.classify(file, preview, AiCleanerSettingsState.getInstance());
            handleResult(file, result, interactive);
        });
    }

    public void scanProject() {
        String basePath = project.getBasePath();
        if (basePath == null) {
            notify("AI File Cleaner", "Project has no base path.", NotificationType.WARNING);
            return;
        }

        notify("AI File Cleaner", "Project scan started.", NotificationType.INFORMATION);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            Path base = Path.of(basePath);
            int[] scanned = {0};
            try {
                Files.walkFileTree(base, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                        if (!dir.equals(base) && SKIPPED_DIRECTORIES.contains(name)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (scanned[0] >= 5000) {
                            return FileVisitResult.TERMINATE;
                        }
                        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
                        if (virtualFile != null) {
                            scanned[0]++;
                            inspectFile(virtualFile, false);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
                notify("AI File Cleaner", "Project scan queued " + scanned[0] + " files.", NotificationType.INFORMATION);
            } catch (IOException e) {
                notify("AI File Cleaner", "Project scan failed: " + e.getMessage(), NotificationType.ERROR);
            }
        });
    }

    public List<SuspectFileRecord> getSuspectRecords() {
        return new ArrayList<>(suspectRecords);
    }

    public void clearRecords() {
        suspectRecords.clear();
    }

    public void moveToQuarantine(String path) {
        String basePath = project.getBasePath();
        if (basePath == null) {
            notify("AI File Cleaner", "Project has no base path.", NotificationType.WARNING);
            return;
        }

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                Path source = Path.of(path);
                if (!Files.exists(source)) {
                    notify("AI File Cleaner", "File no longer exists: " + path, NotificationType.WARNING);
                    return;
                }
                Path quarantineRoot = Path.of(basePath)
                        .resolve(AiCleanerSettingsState.getInstance().getQuarantineDirName())
                        .resolve(LocalDate.now().toString());
                Files.createDirectories(quarantineRoot);
                Path target = uniqueTarget(quarantineRoot.resolve(source.getFileName()));
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                refresh(source);
                refresh(target);
                notify("AI File Cleaner", "Moved to quarantine: " + target, NotificationType.INFORMATION);
            } catch (IOException e) {
                notify("AI File Cleaner", "Failed to move file: " + e.getMessage(), NotificationType.ERROR);
            }
        });
    }

    public void deletePath(String path) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                deleteRecursively(Path.of(path));
                refresh(Path.of(path));
                notify("AI File Cleaner", "Deleted: " + path, NotificationType.INFORMATION);
            } catch (IOException e) {
                notify("AI File Cleaner", "Failed to delete: " + e.getMessage(), NotificationType.ERROR);
            }
        });
    }

    public void handleConfigFile(String path) {
        String basePath = project.getBasePath();
        if (basePath == null) {
            notify("AI File Cleaner", "Project has no base path.", NotificationType.WARNING);
            return;
        }

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                Path base = Path.of(basePath);
                Path file = Path.of(path);
                addToGitignore(base, file);
                VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
                if (virtualFile != null) {
                    markConfigDirectoryExcluded(virtualFile);
                }
                notify("AI File Cleaner", "Added config path to .gitignore/exclude: " + base.relativize(file), NotificationType.INFORMATION);
            } catch (IOException | RuntimeException e) {
                notify("AI File Cleaner", "Failed to ignore/exclude config file: " + e.getMessage(), NotificationType.ERROR);
            }
        });
    }

    private void scheduleInspection(VirtualFile file, boolean interactive) {
        if (file == null || !file.isValid() || file.isDirectory()) {
            return;
        }
        String path = file.getPath();
        if (!pendingPaths.add(path)) {
            return;
        }
        AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
            pendingPaths.remove(path);
            inspectFile(file, interactive);
        }, 700, TimeUnit.MILLISECONDS);
    }

    private void handleResult(VirtualFile file, ClassificationResult result, boolean interactive) {
        if (!result.isActionable() && !interactive) {
            return;
        }

        AiCleanerSettingsState settings = AiCleanerSettingsState.getInstance();
        switch (result.getCategory()) {
            case TEMP -> {
                if (settings.isAutoQuarantineTempFiles()) {
                    moveToQuarantine(file.getPath());
                } else {
                    rememberAndNotify(file, result);
                }
            }
            case AI_GENERATED_USELESS -> {
                if (settings.isAutoQuarantineUselessAiFiles() && result.getConfidence() >= 0.85) {
                    moveToQuarantine(file.getPath());
                } else {
                    rememberAndNotify(file, result);
                }
            }
            case PROJECT_CONFIG, AI_CONFIG -> {
                if (settings.isAutoHandleConfigFiles()) {
                    handleConfigFile(file.getPath());
                }
                if (interactive) {
                    rememberAndNotify(file, result);
                }
            }
            case SUSPICIOUS, UNKNOWN -> rememberAndNotify(file, result);
            case NORMAL -> {
                if (interactive) {
                    notify("AI File Cleaner", "No cleanup needed for " + file.getName(), NotificationType.INFORMATION);
                }
            }
        }
    }

    private void rememberAndNotify(VirtualFile file, ClassificationResult result) {
        remember(file, result);
        Notification notification = NotificationGroupManager.getInstance()
                .getNotificationGroup("AI File Cleaner")
                .createNotification(
                        "AI File Cleaner found " + result.getCategory().getLabel(),
                        file.getPath() + "<br/>" + escapeHtml(result.getReason()),
                        notificationType(result.getCategory())
                );
        notification.addAction(new com.intellij.notification.NotificationAction("Move to quarantine") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                moveToQuarantine(file.getPath());
                notification.expire();
            }
        });
        notification.addAction(new com.intellij.notification.NotificationAction("Delete") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                deletePath(file.getPath());
                notification.expire();
            }
        });
        if (result.getCategory() == FileCategory.PROJECT_CONFIG || result.getCategory() == FileCategory.AI_CONFIG) {
            notification.addAction(new com.intellij.notification.NotificationAction("Add to ignore/exclude") {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                    handleConfigFile(file.getPath());
                    notification.expire();
                }
            });
        }
        notification.notify(project);
    }

    private void remember(VirtualFile file, ClassificationResult result) {
        suspectRecords.removeIf(record -> record.getPath().equals(file.getPath()));
        suspectRecords.add(0, new SuspectFileRecord(file.getPath(), result));
        if (suspectRecords.size() > 200) {
            suspectRecords.subList(200, suspectRecords.size()).clear();
        }
    }

    private boolean isCandidate(VirtualFile file) {
        if (file == null || !file.isValid() || file.isDirectory()) {
            return false;
        }
        String path = file.getPath().toLowerCase(Locale.ROOT).replace('\\', '/');
        if (path.contains("/.git/") || path.contains("/node_modules/") || path.contains("/.ai-cleaner-quarantine/")) {
            return false;
        }
        return ProjectRootManager.getInstance(project).getFileIndex().isInContent(file)
                || project.getBasePath() != null && path.startsWith(project.getBasePath().toLowerCase(Locale.ROOT).replace('\\', '/'));
    }

    private String readPreview(VirtualFile file) {
        int maxBytes = AiCleanerSettingsState.getInstance().getMaxPreviewBytes();
        if (file.getFileType().isBinary()) {
            return "";
        }
        try (InputStream inputStream = file.getInputStream()) {
            byte[] bytes = inputStream.readNBytes(maxBytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ignored) {
            return "";
        }
    }

    private void addToGitignore(Path base, Path file) throws IOException {
        if (!file.startsWith(base)) {
            return;
        }
        Path gitignore = base.resolve(".gitignore");
        String entry = gitignoreEntry(base, file);
        String existing = Files.exists(gitignore) ? Files.readString(gitignore) : "";
        if (containsGitignoreEntry(existing, entry)) {
            return;
        }
        StringBuilder builder = new StringBuilder(existing);
        if (!existing.endsWith("\n") && !existing.isEmpty()) {
            builder.append('\n');
        }
        if (!existing.contains("# AI File Cleaner")) {
            builder.append("\n# AI File Cleaner\n");
        }
        builder.append(entry).append('\n');
        Files.writeString(gitignore, builder.toString(), StandardCharsets.UTF_8);
        refresh(gitignore);
    }

    private String gitignoreEntry(Path base, Path file) {
        String rel = base.relativize(file).toString().replace('\\', '/');
        if (rel.startsWith(".cursor/")) {
            return ".cursor/";
        }
        if (rel.startsWith(".windsurf/")) {
            return ".windsurf/";
        }
        if (rel.startsWith(".continue/")) {
            return ".continue/";
        }
        if (rel.startsWith(".claude/")) {
            return ".claude/";
        }
        if (rel.startsWith(".vscode/")) {
            return ".vscode/";
        }
        if (rel.startsWith(".idea/")) {
            return rel;
        }
        return Files.isDirectory(file) && !rel.endsWith("/") ? rel + "/" : rel;
    }

    private boolean containsGitignoreEntry(String existing, String entry) {
        for (String line : existing.split("\\R")) {
            if (line.trim().equals(entry)) {
                return true;
            }
        }
        return false;
    }

    private void markConfigDirectoryExcluded(VirtualFile file) {
        VirtualFile directory = file.isDirectory() ? file : file.getParent();
        if (directory == null || !shouldExclude(directory)) {
            return;
        }

        UIUtil.invokeLaterIfNeeded(() -> WriteCommandAction.runWriteCommandAction(project, () -> {
            for (Module module : ModuleManager.getInstance(project).getModules()) {
                ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
                boolean changed = false;
                try {
                    for (ContentEntry entry : model.getContentEntries()) {
                        VirtualFile contentRoot = entry.getFile();
                        if (contentRoot != null && isAncestor(contentRoot, directory)) {
                            entry.addExcludeFolder(directory);
                            changed = true;
                            break;
                        }
                    }
                    if (changed) {
                        model.commit();
                    } else {
                        model.dispose();
                    }
                } catch (RuntimeException e) {
                    model.dispose();
                    throw e;
                }
            }
        }));
    }

    private boolean shouldExclude(VirtualFile directory) {
        String name = directory.getName().toLowerCase(Locale.ROOT);
        String path = directory.getPath().toLowerCase(Locale.ROOT).replace('\\', '/');
        return Set.of(".cursor", ".windsurf", ".continue", ".claude", ".vscode", "build", "dist", "out", "target")
                .contains(name)
                || path.endsWith("/.gradle");
    }

    private boolean isAncestor(VirtualFile root, VirtualFile child) {
        String rootPath = root.getPath();
        String childPath = child.getPath();
        return childPath.equals(rootPath) || childPath.startsWith(rootPath + "/");
    }

    private void notify(String title, String content, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("AI File Cleaner")
                .createNotification(title, content, type)
                .notify(project);
    }

    private NotificationType notificationType(FileCategory category) {
        return switch (category) {
            case SUSPICIOUS, UNKNOWN -> NotificationType.WARNING;
            case TEMP, AI_GENERATED_USELESS -> NotificationType.INFORMATION;
            case PROJECT_CONFIG, AI_CONFIG, NORMAL -> NotificationType.INFORMATION;
        };
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private Path uniqueTarget(Path target) throws IOException {
        if (!Files.exists(target)) {
            return target;
        }
        String fileName = target.getFileName().toString();
        String stem = fileName;
        String extension = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            stem = fileName.substring(0, dot);
            extension = fileName.substring(dot);
        }
        for (int i = 1; i < 1000; i++) {
            Path candidate = target.resolveSibling(stem + "-" + i + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IOException("Could not allocate unique quarantine target for " + target);
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        if (Files.isRegularFile(path)) {
            Files.delete(path);
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void refresh(Path path) {
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
        Path parent = path.getParent();
        if (parent != null) {
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(parent);
        }
    }

    @Override
    public void dispose() {
        pendingPaths.clear();
        suspectRecords.clear();
    }
}
