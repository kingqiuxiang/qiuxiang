package com.lingce.cleaner.api

import com.intellij.openapi.diagnostic.Logger
import com.lingce.cleaner.settings.CleanerSettingsState
import org.json.JSONObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.io.path.name

class AiFileDetectorClient {
    private val log = Logger.getInstance(AiFileDetectorClient::class.java)
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()

    data class ApiClassification(val label: String, val reason: String, val confidence: Double)

    fun detect(path: String, sampleContent: String, settings: CleanerSettingsState.State): ApiClassification? {
        if (settings.baseUrl.isBlank() || settings.apiKey.isBlank()) return null

        return try {
            val baseUrl = settings.baseUrl.trimEnd('/')
            val endpoint = URI.create("$baseUrl/chat/completions")
            val prompt = """
                Classify whether this file is likely AI-generated junk, human-meaningful, or suspicious.
                Return strict JSON only:
                {"label":"AI|HUMAN|SUSPICIOUS","reason":"short reason","confidence":0.0}
                
                File path: $path
                File content sample:
                ${sampleContent.take(3500)}
            """.trimIndent()

            val requestBody = JSONObject()
                .put("model", "gpt-4o-mini")
                .put("temperature", 0)
                .put(
                    "messages",
                    org.json.JSONArray()
                        .put(JSONObject().put("role", "system").put("content", "You are a strict file classifier."))
                        .put(JSONObject().put("role", "user").put("content", prompt))
                )
                .toString()

            val request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${settings.apiKey}")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                log.warn("AI detector status ${response.statusCode()} for ${path.substringAfterLast('/')}")
                return null
            }

            val body = JSONObject(response.body())
            val messageContent = body
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
                .orEmpty()

            val cleaned = stripMarkdownFences(messageContent)
            val json = JSONObject(cleaned)
            ApiClassification(
                label = json.optString("label", "HUMAN").uppercase(),
                reason = json.optString("reason", "api classified"),
                confidence = json.optDouble("confidence", 0.6)
            )
        } catch (ex: Exception) {
            log.debug("AI detector request failed for ${path.substringAfterLast('/')}: ${ex.message}")
            null
        }
    }

    private fun stripMarkdownFences(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val firstNewLine = trimmed.indexOf('\n')
        if (firstNewLine == -1) return trimmed.removePrefix("```").removeSuffix("```").trim()
        return trimmed.substring(firstNewLine + 1).removeSuffix("```").trim()
    }
}
