package com.lingce.cleaner;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class AiCleanerStartupActivity implements StartupActivity.DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
        AiCleanerSettings.StateData settings = AiCleanerSettings.getInstance().getState();
        if (!settings.enabled) {
            return;
        }

        AiCleanerService service = project.getService(AiCleanerService.class);
        if (settings.scanOnProjectOpen) {
            service.scanProject();
        }

        if (settings.watchNewFiles) {
            project.getMessageBus()
                .connect(project)
                .subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
                    @Override
                    public void after(@NotNull List<? extends VFileEvent> events) {
                        AiCleanerSettings.StateData current = AiCleanerSettings.getInstance().getState();
                        if (!current.enabled || !current.watchNewFiles) {
                            return;
                        }

                        for (VFileEvent event : events) {
                            if (event instanceof VFileCreateEvent) {
                                VirtualFile file = event.getFile();
                                if (file != null) {
                                    service.inspectNewFile(file);
                                }
                            }
                        }
                    }
                });
        }
    }
}
