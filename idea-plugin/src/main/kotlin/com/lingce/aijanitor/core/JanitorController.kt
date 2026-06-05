package com.lingce.aijanitor.core

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.lingce.aijanitor.model.CleanupAction
import com.lingce.aijanitor.model.ScanItem
import com.lingce.aijanitor.settings.AiJanitorSettings
import java.util.concurrent.CopyOnWriteArrayList

/** Project-level coordinator between the scanner, AI, cleanup service and the UI. */
class JanitorController(private val project: Project) {

    fun interface Listener {
        fun onItemsUpdated(items: List<ScanItem>, message: String)
    }

    @Volatile
    var items: List<ScanItem> = emptyList()
        private set

    @Volatile
    var scanning: Boolean = false
        private set

    private val listeners = CopyOnWriteArrayList<Listener>()

    fun addListener(listener: Listener) { listeners.add(listener) }
    fun removeListener(listener: Listener) { listeners.remove(listener) }

    private fun notifyListeners(message: String) {
        val snapshot = items
        ApplicationManager.getApplication().invokeLater {
            listeners.forEach { it.onItemsUpdated(snapshot, message) }
        }
    }

    /** Runs a full scan (heuristics + optional AI) on a background thread. */
    fun scan() {
        if (scanning) return
        scanning = true
        notifyListeners("正在扫描…")
        object : Task.Backgroundable(project, "AI 文件清理：扫描项目", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "扫描项目文件…"
                val result = ProjectScanner(project).scan(indicator)
                val settings = AiJanitorSettings.getInstance()
                if (settings.aiConfigured() && result.items.isNotEmpty()) {
                    indicator.text = "调用 AI 进行智能识别…"
                    val client = AiClient(settings.state.baseUrl, settings.apiKey, settings.state.model)
                    AiClassifier(client, settings.state.aiBatchSize).refine(result.items, result.snippets, indicator)
                }
                val actionable = result.items.filter { it.category.name != "NORMAL" }
                items = actionable
                val msg = "扫描完成：共 ${result.scannedCount} 个文件，发现 ${actionable.size} 个待处理。"
                notifyListeners(msg)
            }

            override fun onFinished() {
                scanning = false
            }

            override fun onThrowable(error: Throwable) {
                scanning = false
                notifyListeners("扫描失败：${error.message}")
            }
        }.queue()
    }

    /** Applies the given items' actions; removes processed items from the list. */
    fun applyItems(toApply: List<ScanItem>) {
        if (toApply.isEmpty()) return
        ApplicationManager.getApplication().invokeLater {
            val report = CleanupService(project).apply(toApply)
            val processed = toApply.filter { it.action != CleanupAction.KEEP }.toSet()
            items = items.filterNot { it in processed && report.errors.none { e -> e.startsWith(it.relativePath) } }
            notify(report.summary(), if (report.errors.isEmpty()) NotificationType.INFORMATION else NotificationType.WARNING)
            notifyListeners(report.summary())
        }
    }

    private fun notify(content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("AI File Janitor")
            .createNotification("AI 文件清道夫", content, type)
            .notify(project)
    }

    companion object {
        fun getInstance(project: Project): JanitorController =
            project.getService(JanitorController::class.java)
    }
}
