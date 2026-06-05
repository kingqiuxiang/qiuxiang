package com.lingce.cleankeeper.listener

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.lingce.cleankeeper.service.CleanupService
import com.lingce.cleankeeper.settings.CleanKeeperSettings

class ProjectStartupActivity : StartupActivity {

    override fun runActivity(project: Project) {
        if (!CleanKeeperSettings.getInstance().enabled) return

        val connection = project.messageBus.connect(project)
        connection.subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: MutableList<out VFileEvent>) {
                    if (!CleanKeeperSettings.getInstance().autoScanOnImport) return

                    ApplicationManager.getApplication().invokeLater {
                        for (event in events) {
                            val file = event.file ?: continue
                            if (event.isFromRefresh) continue
                            when (event) {
                                is VFileCreateEvent,
                                is VFileCopyEvent,
                                is VFileMoveEvent,
                                -> handleNewFile(project, file)
                                else -> {}
                            }
                        }
                    }
                }
            },
        )
    }

    private fun handleNewFile(project: Project, file: VirtualFile) {
        if (file.isDirectory) return
        CleanupService.getInstance(project).scanAndHandle(file)
    }
}
