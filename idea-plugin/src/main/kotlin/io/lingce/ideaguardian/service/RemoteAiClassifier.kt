package io.lingce.ideaguardian.service

import io.lingce.ideaguardian.settings.AiFileGuardianSettingsState
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class RemoteAiClassifier(private val settings: AiFileGuardianSettingsState) {
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build()

    fun classify(filePath: String, snippet: String): Boolean? {
        val key = settings.apiKey.trim()
        val base = settings.baseUrl.trim()
        if (key.isBlank() || base.isBlank()) {
            return null
        }

        val endpoint = if (base.endsWith("/chat/completions")) {
            base
        } else {
            "${base.trimEnd('/')}/chat/completions"
        }

        val prompt = """
            你是文件治理助手。请判断下面片段是否大概率是 AI 生成内容。
            只允许回答 AI 或 HUMAN。
            文件路径: $filePath
            文件内容片段:
            $snippet
        """.trimIndent()

        val payload = """
            {
              "model":"gpt-4o-mini",
              "temperature":0,
              "messages":[
                {"role":"system","content":"你是严格分类器。"},
                {"role":"user","content":"${escapeJson(prompt)}"}
              ]
            }
        """.trimIndent()

        val request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $key")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()

        return try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                null
            } else {
                val answer = extractContent(response.body())?.trim()?.uppercase()
                when {
                    answer == null -> null
                    answer.contains("HUMAN") -> false
                    answer.contains("AI") -> true
                    else -> null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractContent(json: String): String? {
        val regex = Regex("\"content\"\\s*:\\s*\"([^\"]+)\"")
        return regex.find(json)?.groups?.get(1)?.value
    }

    private fun escapeJson(raw: String): String {
        return raw
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("\t", " ")
    }
}
