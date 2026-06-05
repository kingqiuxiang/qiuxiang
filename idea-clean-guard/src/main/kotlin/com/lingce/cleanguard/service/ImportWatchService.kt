package com.lingce.cleanguard.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.messages.MessageBusConnection
import com.lingce.cleanguard.detection.FileClassifier
import com.lingce.cleanguard.model.ClassifiedFile
import com.lingce.cleanguard.model.FileCategory
import com.lingce.cleanguard.settings.CleanGuardSettings
import com.lingce.cleanguard.ui.CleanGuardToolWindowFactory
import java.util.concurrent.ConcurrentHashMap

class ImportWatchService(private val project: Project) {

    private val cleanup = CleanupService(project)
    private val scanService = ScanService(project)
    private var connection: MessageBusConnection? = null
    private val recentlyProcessed = ConcurrentHashMap.newKeySet<String>()

    fun start() {
        if (connection != null) return
        connection = project.messageBus.connect(project).also { conn ->
            conn.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                override fun after(events: MutableList<out VFileEvent>) {
                    if (!CleanGuardSettings.getInstance().state.enabled) return
                    events.filter { it is VFileCreateEvent || it is VFileCopyEvent }
                        .mapNotNull { it.file }
                        .forEach { handleNewFile(it) }
                }
            })
        }
    }

    fun stop() {
        connection?.disconnect()
        connection = null
    }

    private fun handleNewFile(file: VirtualFile) {
        if (file.isDirectory && file.name !in setOf(".cursor", ".copilot", ".continue", ".windsurf")) {
            return
        }
        if (FileClassifier.isUnderIgnoredVcs(file)) return

        val relative = project.basePath?.let { base ->
            com.intellij.openapi.vfs.VfsUtil.getRelativePath(
                file,
                com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(base)!!,
            )?.replace('\\', '/')
        } ?: return

        if (!recentlyProcessed.add(relative)) return

        ApplicationManager.getApplication().executeOnPooledThread {
            val classified = scanService.classifyFile(file)
            if (classified.category == FileCategory.SAFE) return@executeOnPooledThread

            ApplicationManager.getApplication().invokeLater {
                val acted = cleanup.applyAutoAction(classified)
                if (!acted && classified.category == FileCategory.SUSPICIOUS) {
                    CleanGuardNotifier.warn(
                        project,
                        "发现可疑文件: $relative — 请在 Clean Guard 面板中处理",
                    )
                }
                CleanGuardToolWindowFactory.refresh(project)
                scanService.results.add(classified)
            }
        }
    }

    fun processManual(classified: ClassifiedFile, action: ManualAction): Boolean {
        return when (action) {
            ManualAction.DELETE -> cleanup.delete(classified)
            ManualAction.QUARANTINE -> cleanup.quarantine(classified)
            ManualAction.EXCLUDE -> ExcludeService(project).exclude(classified)
        }.also { success ->
            if (success) {
                scanService.results.removeIf { it.path == classified.path }
                CleanGuardToolWindowFactory.refresh(project)
            }
        }
    }

    enum class ManualAction {
        DELETE, QUARANTINE, EXCLUDE
    }

    companion object {
        private val active = ConcurrentHashMap<String, ImportWatchService>()

        fun getOrStart(project: Project): ImportWatchService =
            active.computeIfAbsent(project.locationHash.toString()) {
                ImportWatchService(project).also { it.start() }
            }

        fun stopAll() {
            active.values.forEach { it.stop() }
            active.clear()
        }
    }
}
