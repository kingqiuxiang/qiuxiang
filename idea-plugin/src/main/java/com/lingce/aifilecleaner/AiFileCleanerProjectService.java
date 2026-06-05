package com.lingce.aifilecleaner;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.util.messages.MessageBusConnection;
import com.lingce.aifilecleaner.classifier.FileClassification;
import com.lingce.aifilecleaner.classifier.FileClassifier;
import com.lingce.aifilecleaner.classifier.FileSample;
import com.lingce.aifilecleaner.classifier.OpenAiFileClassifier;
import com.lingce.aifilecleaner.settings.AiFileCleanerSettings;
import com.lingce.aifilecleaner.util.FileDispositionManager;
import com.lingce.aifilecleaner.util.FileSampleFactory;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service(Service.Level.PROJECT)
public final class AiFileCleanerProjectService implements Disposable {
    private static final int MAX_FINDINGS = 500;

    private final Project project;
    private final FileClassifier classifier;
    private final FileDispositionManager dispositionManager;
    private final Map<String, Finding> findings = Collections.synchronizedMap(new LinkedHashMap<>());
    private MessageBusConnection connection;
    private volatile boolean started;

    public AiFileCleanerProjectService(Project project) {
        this.project = project;
        this.classifier = new OpenAiFileClassifier();
        this.dispositionManager = new FileDispositionManager(project);
    }

    public void start() {
        if (started || project.isDisposed()) {
            return;
        }
        started = true;
        connection = project.getMessageBus().connect(this);
        connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                if (!AiFileCleanerSettings.getInstance().getState().scanNewFiles) {
                    return;
                }
                List<VirtualFile> changedFiles = new ArrayList<>();
                for (VFileEvent event : events) {
                    if (event instanceof VFilePropertyChangeEvent propertyEvent
                            && VirtualFile.PROP_NAME.equals(propertyEvent.getPropertyName())) {
                        continue;
                    }
                    VirtualFile file = event.getFile();
                    if (file != null && file.isValid()) {
                        changedFiles.add(file);
                    }
                }
                if (!changedFiles.isEmpty()) {
                    scanFiles(changedFiles, "Scan changed files");
                }
            }
        });

        if (AiFileCleanerSettings.getInstance().getState().scanOnProjectOpen) {
            scanProject();
        }
    }

    public void scanProject() {
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null || !baseDir.isValid()) {
            return;
        }
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "AI File Cleaner: scanning project", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                scanRecursively(baseDir, indicator);
            }
        });
    }

    public void scanFiles(Collection<VirtualFile> files, String title) {
        if (files == null || files.isEmpty()) {
            return;
        }
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "AI File Cleaner: " + title, true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                for (VirtualFile file : files) {
                    indicator.checkCanceled();
                    inspect(file);
                    if (file.isDirectory()) {
                        for (VirtualFile child : file.getChildren()) {
                            indicator.checkCanceled();
                            inspect(child);
                        }
                    }
                }
            }
        });
    }

    public List<Finding> getFindings() {
        synchronized (findings) {
            return new ArrayList<>(findings.values());
        }
    }

    public FileDispositionManager getDispositionManager() {
        return dispositionManager;
    }

    public void removeFinding(String path) {
        findings.remove(path);
    }

    @Override
    public void dispose() {
        findings.clear();
    }

    private void scanRecursively(VirtualFile file, ProgressIndicator indicator) {
        indicator.checkCanceled();
        if (shouldSkip(file)) {
            return;
        }
        inspect(file);
        if (!file.isDirectory() || isConfigDirectoryBoundary(file)) {
            return;
        }
        for (VirtualFile child : file.getChildren()) {
            scanRecursively(child, indicator);
        }
    }

    private void inspect(VirtualFile file) {
        if (project.isDisposed() || shouldSkip(file)) {
            return;
        }
        AiFileCleanerSettings.PluginState settings = AiFileCleanerSettings.getInstance().getState();
        FileSample sample = FileSampleFactory.fromVirtualFile(project, file, settings.maxSampleBytes);
        if (sample == null) {
            return;
        }
        FileClassification classification = classifier.classify(sample);
        switch (classification.kind()) {
            case SAFE -> {
            }
            case TEMPORARY -> {
                if (settings.autoCleanTemporaryFiles && classification.recommendedAction() == FileClassification.RecommendedAction.DELETE) {
                    dispositionManager.delete(file);
                } else {
                    rememberAndNotify(file, classification);
                }
            }
            case PROJECT_CONFIG, AI_CONFIG -> {
                if (settings.autoIgnoreConfigFiles) {
                    dispositionManager.ignoreOrExclude(file, classification);
                } else {
                    rememberAndNotify(file, classification);
                }
            }
            case AI_GENERATED -> {
                if (settings.autoDeleteObviousAiTrash
                        && classification.recommendedAction() == FileClassification.RecommendedAction.DELETE
                        && classification.confidence() >= 0.8) {
                    dispositionManager.delete(file);
                } else {
                    rememberAndNotify(file, classification);
                }
            }
            case SUSPICIOUS -> rememberAndNotify(file, classification);
        }
    }

    private void rememberAndNotify(VirtualFile file, FileClassification classification) {
        Finding finding = new Finding(file.getPath(), file, classification, Instant.now());
        synchronized (findings) {
            findings.put(file.getPath(), finding);
            while (findings.size() > MAX_FINDINGS) {
                String firstKey = findings.keySet().iterator().next();
                findings.remove(firstKey);
            }
        }
        notifyFinding(finding);
    }

    private void notifyFinding(Finding finding) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed() || !finding.virtualFile().isValid()) {
                return;
            }
            Notification notification = NotificationGroupManager.getInstance()
                    .getNotificationGroup("AI File Cleaner")
                    .createNotification(
                            "Suspicious local file",
                            finding.classification().kind() + ": " + finding.path() + "\n" + finding.classification().reason(),
                            NotificationType.WARNING
                    );
            notification.addAction(NotificationAction.createSimple("Move to quarantine", () -> {
                dispositionManager.quarantine(finding.virtualFile());
                removeFinding(finding.path());
                notification.expire();
            }));
            notification.addAction(NotificationAction.createSimple("Delete", () -> {
                dispositionManager.delete(finding.virtualFile());
                removeFinding(finding.path());
                notification.expire();
            }));
            notification.addAction(NotificationAction.createSimple("Ignore/exclude", () -> {
                dispositionManager.ignoreOrExclude(finding.virtualFile(), finding.classification());
                removeFinding(finding.path());
                notification.expire();
            }));
            notification.notify(project);
        });
    }

    private static boolean shouldSkip(VirtualFile file) {
        String name = file.getName();
        if (!file.isInLocalFileSystem()) {
            return true;
        }
        if (file.getPath().contains("/.ai-file-cleaner/")) {
            return true;
        }
        if (!file.isDirectory()) {
            return false;
        }
        return name.equals(".git")
                || name.equals("node_modules")
                || name.equals("dist")
                || name.equals("build")
                || name.equals("out")
                || name.equals("target")
                || name.equals(".gradle")
                || name.equals(".ai-file-cleaner");
    }

    private static boolean isConfigDirectoryBoundary(VirtualFile file) {
        String name = file.getName();
        return name.equals(".idea")
                || name.equals(".vscode")
                || name.equals(".settings")
                || name.equals(".cursor")
                || name.equals(".windsurf")
                || name.equals(".continue")
                || name.equals(".claude");
    }
}
