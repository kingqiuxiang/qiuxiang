package com.lingce.aijanitor.core

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Minimal client for any OpenAI-compatible `/chat/completions` endpoint.
 */
class AiClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) {
    private val gson = Gson()
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build()

    /** Sends a chat completion request and returns the assistant message content. */
    fun chat(systemPrompt: String, userPrompt: String): String {
        val body = JsonObject().apply {
            addProperty("model", model)
            addProperty("temperature", 0.0)
            add("messages", gson.toJsonTree(
                listOf(
                    mapOf("role" to "system", "content" to systemPrompt),
                    mapOf("role" to "user", "content" to userPrompt),
                )
            ))
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint()))
            .timeout(Duration.ofSeconds(120))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() / 100 != 2) {
            throw RuntimeException("AI 请求失败 (HTTP ${response.statusCode()}): ${response.body().take(500)}")
        }
        val json = JsonParser.parseString(response.body()).asJsonObject
        return json.getAsJsonArray("choices")
            .first().asJsonObject
            .getAsJsonObject("message")
            .get("content").asString
    }

    private fun endpoint(): String {
        val trimmed = baseUrl.trimEnd('/')
        return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
    }

    companion object {
        private val LOG = logger<AiClient>()
    }
}
