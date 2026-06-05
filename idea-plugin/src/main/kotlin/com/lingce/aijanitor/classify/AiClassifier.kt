package com.lingce.aijanitor.classify

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VirtualFile
import com.lingce.aijanitor.settings.AiJanitorSettings
import com.lingce.aijanitor.util.MiniJson
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale

/**
 * Calls an OpenAI-compatible chat-completions endpoint to classify a file.
 * Purely best-effort: any failure returns null so the caller keeps the
 * heuristic verdict.
 */
class AiClassifier(private val settings: AiJanitorSettings) {

    private val log = logger<AiClassifier>()

    private val http: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build()
    }

    fun isAvailable(): Boolean = settings.isAiConfigured()

    fun classify(file: VirtualFile, projectBasePath: String?): ClassificationResult? {
        if (!isAvailable()) return null
        val snippet = readSnippet(file) ?: return null
        return try {
            val body = buildRequestBody(file, snippet, projectBasePath)
            val request = HttpRequest.newBuilder()
                .uri(URI.create(chatCompletionsUrl()))
                .timeout(Duration.ofSeconds(settings.requestTimeoutSeconds.toLong()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${settings.apiKey}")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                log.warn("AI classify HTTP ${response.statusCode()}: ${response.body().take(300)}")
                return null
            }
            parseResponse(file, response.body())
        } catch (e: Exception) {
            log.warn("AI classify failed for ${file.path}: ${e.message}")
            null
        }
    }

    private fun chatCompletionsUrl(): String {
        val base = settings.baseUrl.trimEnd('/')
        return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
    }

    private fun buildRequestBody(file: VirtualFile, snippet: String, basePath: String?): String {
        val rel = basePath?.let {
            file.path.removePrefix(it.trimEnd('/')).trimStart('/')
        } ?: file.name
        val userContent = buildString {
            append("File path: ").append(rel).append('\n')
            append("File name: ").append(file.name).append('\n')
            append("Size (bytes): ").append(file.length).append('\n')
            append("----- BEGIN CONTENT -----\n")
            append(snippet)
            append("\n----- END CONTENT -----")
        }
        return buildString {
            append('{')
            append("\"model\":").append(MiniJson.quote(settings.model)).append(',')
            append("\"temperature\":0,")
            append("\"messages\":[")
            append('{')
            append("\"role\":\"system\",")
            append("\"content\":").append(MiniJson.quote(SYSTEM_PROMPT))
            append("},")
            append('{')
            append("\"role\":\"user\",")
            append("\"content\":").append(MiniJson.quote(userContent))
            append('}')
            append("]}")
        }
    }

    private fun parseResponse(file: VirtualFile, raw: String): ClassificationResult? {
        val root = MiniJson.asObject(MiniJson.parse(raw)) ?: return null
        val choices = MiniJson.asArray(root["choices"]) ?: return null
        val first = MiniJson.asObject(choices.firstOrNull()) ?: return null
        val message = MiniJson.asObject(first["message"]) ?: return null
        val content = message["content"] as? String ?: return null
        val jsonText = MiniJson.extractFirstObject(content) ?: return null
        val parsed = MiniJson.asObject(MiniJson.parse(jsonText)) ?: return null

        val category = parseCategory(parsed["category"] as? String) ?: return null
        val action = parseAction(parsed["action"] as? String) ?: category.defaultAction()
        val confidence = (parsed["confidence"] as? Double)?.coerceIn(0.0, 1.0) ?: 0.6
        val reason = (parsed["reason"] as? String)?.takeIf { it.isNotBlank() } ?: "AI 判定"
        return ClassificationResult(file, category, action, confidence, reason, "ai")
    }

    private fun parseCategory(value: String?): FileCategory? = when (value?.uppercase(Locale.ROOT)) {
        "AI_GENERATED_USELESS", "AI_GENERATED", "USELESS" -> FileCategory.AI_GENERATED_USELESS
        "TEMP", "TEMPORARY" -> FileCategory.TEMP
        "PROJECT_CONFIG", "CONFIG" -> FileCategory.PROJECT_CONFIG
        "AI_CONFIG" -> FileCategory.AI_CONFIG
        "SUSPICIOUS", "UNKNOWN" -> FileCategory.SUSPICIOUS
        "KEEP", "SOURCE" -> FileCategory.KEEP
        else -> null
    }

    private fun parseAction(value: String?): RecommendedAction? = when (value?.uppercase(Locale.ROOT)) {
        "DELETE" -> RecommendedAction.DELETE
        "IGNORE", "EXCLUDE" -> RecommendedAction.IGNORE
        "QUARANTINE", "MOVE" -> RecommendedAction.QUARANTINE
        "KEEP" -> RecommendedAction.KEEP
        else -> null
    }

    private fun readSnippet(file: VirtualFile): String? {
        if (file.isDirectory || file.length == 0L || file.length > 512 * 1024) return null
        return try {
            val text = String(file.contentsToByteArray(), Charsets.UTF_8)
            if (text.any { it == '\u0000' }) return null
            text.take(settings.maxContentChars)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private val SYSTEM_PROMPT = """
            You are a meticulous repository hygiene assistant embedded in an IDE.
            Given a single file's path and (possibly truncated) content, classify it.

            Return ONLY a compact JSON object, no prose, with this shape:
            {"category": <CATEGORY>, "action": <ACTION>, "confidence": <0..1>, "reason": "<short, <=120 chars>"}

            CATEGORY must be one of:
            - AI_GENERATED_USELESS: raw/un-integrated AI assistant output, scratch
              answers, placeholder/sample code with no project value.
            - TEMP: temporary, build, cache, backup, editor or OS junk files.
            - PROJECT_CONFIG: local/IDE/project config that should not be committed
              (.idea, .vscode, *.iml, local env, editor settings).
            - AI_CONFIG: configuration for AI coding tools (Cursor, Copilot, Aider,
              Claude, Continue, Codeium, Windsurf, etc.).
            - SUSPICIOUS: unexpected, unclear, or potentially unsafe file you cannot
              confidently bucket.
            - KEEP: legitimate source code, assets, docs, or standard project files.

            ACTION must be one of: DELETE, IGNORE, QUARANTINE, KEEP.
            Recommended mapping: AI_GENERATED_USELESS/TEMP->DELETE,
            PROJECT_CONFIG/AI_CONFIG->IGNORE, SUSPICIOUS->QUARANTINE, KEEP->KEEP.
            Be conservative: if a file looks like real, useful source code, choose KEEP.
        """.trimIndent()
    }
}
