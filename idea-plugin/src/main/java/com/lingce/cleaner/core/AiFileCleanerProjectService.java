package com.lingce.cleaner.core;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.lingce.cleaner.ai.AiClassificationClient;
import com.lingce.cleaner.settings.AiFileCleanerSettings;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AiFileCleanerProjectService implements Disposable {
    private static final String NOTIFICATION_GROUP = "AI File Cleaner";
    private static final double AUTO_CONFIDENCE_THRESHOLD = 0.82;

    private final Project project;
    private final LocalFileClassifier localClassifier = new LocalFileClassifier();
    private final FileContentSampler sampler = new FileContentSampler();
    private final AiClassificationClient aiClient = new AiClassificationClient();
    private final IgnoreAndExcludeManager ignoreAndExcludeManager = new IgnoreAndExcludeManager();
    private final FileCleanupOperator cleanupOperator = new FileCleanupOperator();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Set<String> inProgress = ConcurrentHashMap.newKeySet();

    public AiFileCleanerProjectService(Project project) {
        this.project = project;
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        project.getMessageBus()
            .connect(this)
            .subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
                @Override
                public void after(@NotNull List<? extends VFileEvent> events) {
                    AiFileCleanerSettings.StateData settings = settings();
                    if (settings == null || !settings.enabled) {
                        return;
                    }
                    for (VFileEvent event : events) {
                        VirtualFile file = resolveFile(event);
                        if (file != null) {
                            scheduleHandle(file, false);
                        }
                    }
                }
            });
    }

    public void scanProject() {
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) {
            return;
        }
        notifyInfo("AI File Cleaner scan started", "Scanning project files in the background.");
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            VfsUtilCore.visitChildrenRecursively(baseDir, new VirtualFileVisitor<>() {
                @Override
                public boolean visitFile(@NotNull VirtualFile file) {
                    if (project.isDisposed()) {
                        return false;
                    }
                    if (shouldSkipTree(file)) {
                        return false;
                    }
                    if (!file.equals(baseDir)) {
                        handleFile(file, true);
                    }
                    return true;
                }
            });
            notifyInfo("AI File Cleaner scan finished", "Project scan completed.");
        });
    }

    public void quarantine(VirtualFile file) {
        cleanupOperator.quarantine(project, file);
    }

    public void delete(VirtualFile file) {
        cleanupOperator.delete(project, file);
    }

    private void scheduleHandle(VirtualFile file, boolean manualScan) {
        if (!isInsideProject(file) || shouldSkipTree(file)) {
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> handleFile(file, manualScan));
    }

    private void handleFile(VirtualFile file, boolean manualScan) {
        if (!isInsideProject(file) || !file.isValid()) {
            return;
        }
        String key = file.getPath();
        if (!inProgress.add(key)) {
            return;
        }
        try {
            AiFileCleanerSettings.StateData settings = settings();
            if (settings == null || (!settings.enabled && !manualScan)) {
                return;
            }

            String sample = sampler.sample(file, settings.maxBytesForAiAnalysis);
            FileDecision decision = decide(file, sample);
            processDecision(file, decision, settings);
        } finally {
            inProgress.remove(key);
        }
    }

    private FileDecision decide(VirtualFile file, String sample) {
        FileDecision local = localClassifier.classify(project, file, sample);
        if (file.isDirectory()) {
            return local;
        }
        if (local.getCategory() == FileCategory.TEMPORARY
            || local.getCategory() == FileCategory.PROJECT_CONFIG
            || local.getCategory() == FileCategory.AI_CONFIG) {
            return local;
        }

        String relativePath = relativePath(file);
        return aiClient.classify(relativePath, sample)
            .filter(ai -> ai.getConfidence() >= local.getConfidence() || local.getCategory() == FileCategory.KEEP)
            .orElse(local);
    }

    private void processDecision(VirtualFile file, FileDecision decision, AiFileCleanerSettings.StateData settings) {
        if (!decision.isActionable() || !file.isValid()) {
            return;
        }

        switch (decision.getCategory()) {
            case TEMPORARY -> {
                if (settings.autoDeleteTempFiles && decision.getConfidence() >= AUTO_CONFIDENCE_THRESHOLD) {
                    cleanupOperator.delete(project, file);
                } else {
                    notifySuspicious(file, decision);
                }
            }
            case AI_GENERATED_USELESS -> {
                if (settings.autoHandleAiGeneratedFiles && decision.getConfidence() >= AUTO_CONFIDENCE_THRESHOLD) {
                    if (AiFileCleanerSettings.CLEANUP_DELETE.equals(settings.aiGeneratedCleanupMode)) {
                        cleanupOperator.delete(project, file);
                    } else {
                        cleanupOperator.quarantine(project, file);
                    }
                } else {
                    notifySuspicious(file, decision);
                }
            }
            case PROJECT_CONFIG, AI_CONFIG -> {
                if (settings.addConfigFilesToIgnore) {
                    ignoreAndExcludeManager.addToGitignore(project, file);
                }
                if (settings.markConfigDirectoriesExcluded) {
                    ignoreAndExcludeManager.markExcluded(project, file);
                }
                notifyInfo("Configuration file handled", relativePath(file) + " was added to ignore/exclude handling.");
            }
            case SUSPICIOUS -> notifySuspicious(file, decision);
            case KEEP -> {
            }
        }
    }

    private void notifySuspicious(VirtualFile file, FileDecision decision) {
        AiFileCleanerSettings.StateData settings = settings();
        if (settings != null && !settings.notifySuspiciousFiles) {
            return;
        }

        ApplicationManager.getApplication().invokeLater(() -> {
            if (!file.isValid() || project.isDisposed()) {
                return;
            }
            String relativePath = relativePath(file);
            Notification notification = NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification(
                    "Suspicious file detected",
                    relativePath + "<br/>" + decision.getReason(),
                    NotificationType.WARNING
                );
            notification.addAction(NotificationAction.createSimple("Quarantine", () -> {
                cleanupOperator.quarantine(project, file);
                notification.expire();
            }));
            notification.addAction(NotificationAction.createSimple("Delete", () -> {
                cleanupOperator.delete(project, file);
                notification.expire();
            }));
            notification.addAction(NotificationAction.createSimple("Ignore", notification::expire));
            Notifications.Bus.notify(notification, project);
        });
    }

    private void notifyInfo(String title, String content) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }
            Notification notification = NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification(title, content, NotificationType.INFORMATION);
            Notifications.Bus.notify(notification, project);
        });
    }

    private boolean shouldSkipTree(VirtualFile file) {
        String name = file.getName();
        if (".git".equals(name) || "node_modules".equals(name) || "dist".equals(name) || "build".equals(name) || "out".equals(name)) {
            return true;
        }
        String path = file.getPath().replace('\\', '/');
        return path.contains("/.ai-file-cleaner/quarantine/");
    }

    private VirtualFile resolveFile(VFileEvent event) {
        VirtualFile file = event.getFile();
        if (file != null) {
            return file;
        }
        return LocalFileSystem.getInstance().findFileByPath(event.getPath());
    }

    private boolean isInsideProject(VirtualFile file) {
        VirtualFile baseDir = project.getBaseDir();
        return baseDir != null
            && file != null
            && file.isInLocalFileSystem()
            && VfsUtilCore.isAncestor(baseDir, file, false);
    }

    private String relativePath(VirtualFile file) {
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) {
            return file.getPath();
        }
        String relative = VfsUtilCore.getRelativePath(file, baseDir, '/');
        return relative == null ? file.getPath() : relative;
    }

    private AiFileCleanerSettings.StateData settings() {
        return AiFileCleanerSettings.getInstance().getState();
    }

    @Override
    public void dispose() {
    }
}
