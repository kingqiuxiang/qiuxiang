package com.lingce.aicleaner.core

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.thisLogger
import com.lingce.aicleaner.model.FileCategory
import com.lingce.aicleaner.settings.AiCleanerSettings
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * 调用 OpenAI 兼容接口（/chat/completions）对“本地规则无法确定”的文件做二次判定。
 */
class AiClient(private val settings: AiCleanerSettings) {

    private val gson = Gson()
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    data class AiVerdict(val category: FileCategory, val reason: String)

    /**
     * 请求 AI 判定文件分类。失败返回 null（调用方需自行降级到本地规则）。
     */
    fun classify(relativePath: String, sizeBytes: Long, contentSnippet: String): AiVerdict? {
        if (!settings.isAiConfigured) return null

        val prompt = buildPrompt(relativePath, sizeBytes, contentSnippet)
        val body = JsonObject().apply {
            addProperty("model", settings.state.model.ifBlank { "gpt-4o-mini" })
            addProperty("temperature", 0.0)
            add("messages", gson.toJsonTree(
                listOf(
                    mapOf("role" to "system", "content" to SYSTEM_PROMPT),
                    mapOf("role" to "user", "content" to prompt),
                ),
            ))
        }

        return try {
            val url = settings.state.baseUrl.trimEnd('/') + "/chat/completions"
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(40))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${settings.apiKey}")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build()

            val resp = http.send(request, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() !in 200..299) {
                thisLogger().warn("AI classify HTTP ${resp.statusCode()}: ${resp.body().take(300)}")
                return null
            }
            parseVerdict(resp.body())
        } catch (e: Exception) {
            thisLogger().warn("AI classify failed: ${e.message}")
            null
        }
    }

    /** 简单连通性测试，返回可读结果。 */
    fun testConnection(): String {
        return try {
            val v = classify("README.md", 100, "# Hello world")
            if (v != null) "连接成功，模型已返回有效结果。" else "请求已发出但未获得有效结果，请检查 Base URL / Key / Model。"
        } catch (e: Exception) {
            "连接失败：${e.message}"
        }
    }

    private fun parseVerdict(responseBody: String): AiVerdict? {
        val root = JsonParser.parseString(responseBody).asJsonObject
        val content = root.getAsJsonArray("choices")
            ?.firstOrNull()?.asJsonObject
            ?.getAsJsonObject("message")
            ?.get("content")?.asString
            ?: return null

        val json = extractJson(content) ?: return null
        val obj = JsonParser.parseString(json).asJsonObject
        val catName = obj.get("category")?.asString?.trim()?.uppercase() ?: return null
        val reason = obj.get("reason")?.asString ?: "AI 判定"
        val category = FileCategory.entries.firstOrNull { it.name == catName } ?: return null
        return AiVerdict(category, reason)
    }

    /** 从可能带有 ```json 包裹的文本中提取 JSON 对象。 */
    private fun extractJson(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }

    private fun buildPrompt(relativePath: String, sizeBytes: Long, snippet: String): String = buildString {
        appendLine("请判断下面这个文件应归为哪一类，用于清理工作区。")
        appendLine("文件路径: $relativePath")
        appendLine("文件大小: $sizeBytes 字节")
        appendLine("内容片段(可能被截断):")
        appendLine("\"\"\"")
        appendLine(snippet.take(MAX_SNIPPET))
        appendLine("\"\"\"")
        appendLine()
        appendLine("仅返回 JSON：{\"category\": <分类>, \"reason\": <简短中文理由>}")
    }

    companion object {
        private const val MAX_SNIPPET = 4000

        private val SYSTEM_PROMPT = """
            你是一个帮助开发者清理工作区的助手。把文件归入以下分类之一（只能用大写英文枚举名）：
            - AI_GENERATED_USELESS: AI 生成的、对项目无用的产物（草稿、示例、演示、临时脚本、重复内容等）。
            - TMP: 临时文件、缓存、编辑器/构建残留。
            - PROJECT_CONFIG: 项目层面的配置文件（lint/格式化/编辑器等）。
            - AI_CONFIG: AI 编程工具的配置（.cursor、CLAUDE.md、.aider 等）。
            - SUSPICIOUS: 无法判断用途、来源不明或可疑的文件。
            - NORMAL: 正常的、属于项目的源码或资源，应保留。
            判断保守：只要可能是有用的源码/资源，就归 NORMAL。只能输出 JSON。
        """.trimIndent()
    }
}
