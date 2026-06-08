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

    @Volatile
    private var lastDeep: Boolean = false

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
        lastDeep = false
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
                    val aiKeepPatterns = settings.state.aiKeepPatterns.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    AiClassifier(client, settings.state.aiBatchSize, aiKeepPatterns).refine(result.items, result.snippets, indicator)
                }
                items = result.items
                val actionableCount = result.items.count { it.category.name != "NORMAL" }
                val msg = "扫描完成：共 ${result.scannedCount} 个文件，发现 $actionableCount 个待处理。"
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

    /**
     * Runs an AI deep clean: inspects every file (tracked or not), and surfaces
     * only temp files and unreferenced "orphan" files worth removing.
     */
    fun deepScan() {
        if (scanning) return
        scanning = true
        lastDeep = true
        notifyListeners("正在进行 AI 深度清理分析…")
        object : Task.Backgroundable(project, "AI 深度清理：分析引用关系", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "深度扫描项目文件并分析引用关系…"
                val result = DeepCleanAnalyzer(project).analyze(indicator)
                items = result.items
                val msg = "深度清理完成：共分析 ${result.scannedCount} 个文件，发现 ${result.items.size} 个可清理（临时/孤立）。"
                notifyListeners(msg)
            }

            override fun onFinished() {
                scanning = false
            }

            override fun onThrowable(error: Throwable) {
                scanning = false
                notifyListeners("深度清理失败：${error.message}")
            }
        }.queue()
    }

    /** Applies the given items' actions; removes processed items from the list, then refreshes. */
    fun applyItems(toApply: List<ScanItem>) {
        if (toApply.isEmpty()) return
        ApplicationManager.getApplication().invokeLater {
            val report = CleanupService(project).apply(toApply)
            val processed = toApply.filter { it.action != CleanupAction.KEEP }.toSet()
            items = items.filterNot { it in processed && report.errors.none { e -> e.startsWith(it.relativePath) } }
            notify(report.summary(), if (report.errors.isEmpty()) NotificationType.INFORMATION else NotificationType.WARNING)
            notifyListeners(report.summary())
            // Auto-refresh after apply so the user sees the latest state.
            if (processed.isNotEmpty()) {
                if (lastDeep) deepScan() else scan()
            }
        }
    }

    /** Calls AI to analyze a single file and returns a user-facing confirmation string. */
    fun analyzeSingleFile(item: ScanItem, snippet: String, callback: (String) -> Unit) {
        val settings = AiJanitorSettings.getInstance()
        if (!settings.aiConfigured()) {
            callback("AI 未配置，请先在设置中填写 API Key 和 Base URL。")
            return
        }
        object : Task.Backgroundable(project, "AI 分析文件：${item.file.name}", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "正在调用 AI 分析 ${item.relativePath}…"
                try {
                    val client = AiClient(settings.state.baseUrl, settings.apiKey, settings.state.model)
                    val systemPrompt = """
                        你是一个代码仓库清理专家。分析给定文件，判断它是否可以安全删除。
                        返回一个 JSON 对象，格式：
                        {"canDelete": <true|false>, "purpose": "<文件用途，不超过50字>", "suggestion": "<建议操作：保留/删除/转存/移入git排除，不超过30字>", "risk": "<风险说明，不超过50字>"}
                        只返回 JSON，不要额外文字。
                    """.trimIndent()
                    val userPrompt = "文件路径: ${item.relativePath}\n当前分类: ${item.category.display}\n原因: ${item.reason}\n内容片段:\n```\n${snippet.take(1200)}\n```"
                    val content = client.chat(systemPrompt, userPrompt)
                    val json = extractJsonObject(content)
                    if (json != null) {
                        val purpose = json.get("purpose")?.asString ?: "未知"
                        val suggestion = json.get("suggestion")?.asString ?: "无法判断"
                        val risk = json.get("risk")?.asString ?: "无"
                        val canDelete = json.get("canDelete")?.asBoolean ?: false
                        val sb = StringBuilder()
                        sb.appendLine("📄 文件：${item.relativePath}")
                        sb.appendLine("📋 用途：$purpose")
                        sb.appendLine("💡 建议：$suggestion")
                        sb.appendLine("⚠️ 风险：$risk")
                        sb.appendLine("🔍 可安全删除：${if (canDelete) "✅ 是" else "❌ 否"}")
                        callback(sb.toString())
                    } else {
                        callback("AI 返回结果解析失败，原始内容：\n${content.take(500)}")
                    }
                } catch (e: Exception) {
                    callback("AI 分析失败：${e.message}")
                }
            }
        }.queue()
    }

    private fun extractJsonObject(content: String): com.google.gson.JsonObject? {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            com.google.gson.JsonParser.parseString(content.substring(start, end + 1)).asJsonObject
        } catch (_: Exception) {
            null
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
