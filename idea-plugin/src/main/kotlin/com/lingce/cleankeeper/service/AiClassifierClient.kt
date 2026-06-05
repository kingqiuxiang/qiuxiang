package com.lingce.cleankeeper.service

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lingce.cleankeeper.model.ClassificationSource
import com.lingce.cleankeeper.model.FileCategory
import com.lingce.cleankeeper.model.FileClassification
import com.lingce.cleankeeper.settings.CleanKeeperSettings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AiClassifierClient {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    fun classify(
        fileName: String,
        projectRelativePath: String,
        contentPreview: String?,
    ): FileClassification? {
        val settings = CleanKeeperSettings.getInstance()
        if (!settings.isAiConfigured() || !settings.useAiClassification) return null

        val prompt = buildPrompt(fileName, projectRelativePath, contentPreview)
        val body = JsonObject().apply {
            addProperty("model", settings.model)
            add("messages", gson.toJsonTree(listOf(
                mapOf("role" to "system", "content" to SYSTEM_PROMPT),
                mapOf("role" to "user", "content" to prompt),
            )))
            addProperty("temperature", 0.1)
            addProperty("max_tokens", 300)
        }

        val request = Request.Builder()
            .url("${settings.baseUrl}/chat/completions")
            .addHeader("Authorization", "Bearer ${settings.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = JsonParser.parseString(response.body?.string() ?: return null).asJsonObject
                val text = json.getAsJsonArray("choices")
                    ?.get(0)?.asJsonObject
                    ?.getAsJsonObject("message")
                    ?.get("content")?.asString ?: return null
                parseAiResponse(text)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseAiResponse(text: String): FileClassification? {
        val cleaned = text.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return try {
            val json = JsonParser.parseString(cleaned).asJsonObject
            val categoryName = json.get("category")?.asString ?: return null
            val category = runCatching { FileCategory.valueOf(categoryName) }.getOrNull() ?: return null
            val confidence = json.get("confidence")?.asDouble ?: 0.5
            val reason = json.get("reason")?.asString ?: "AI 分类结果"
            FileClassification(category, confidence, reason, ClassificationSource.AI)
        } catch (_: Exception) {
            null
        }
    }

    private fun buildPrompt(fileName: String, path: String, content: String?): String {
        val preview = content?.take(4000) ?: "(binary or empty)"
        return """
            File name: $fileName
            Project path: $path
            Content preview:
            ---
            $preview
            ---
            Classify this file. Respond ONLY with JSON: {"category":"SAFE|TMP_FILE|AI_GENERATED_USELESS|PROJECT_CONFIG|AI_CONFIG|SUSPICIOUS","confidence":0.0-1.0,"reason":"brief reason in Chinese"}
        """.trimIndent()
    }

    companion object {
        private const val SYSTEM_PROMPT = """
            You are a file hygiene assistant for software projects.
            Classify files to help developers keep workspaces clean.
            Categories:
            - SAFE: normal source code or assets
            - TMP_FILE: temp/cache/backup files safe to delete
            - AI_GENERATED_USELESS: AI-generated drafts, scratch files, useless outputs
            - PROJECT_CONFIG: local env/secrets/IDE configs that should be gitignored
            - AI_CONFIG: AI tool configs (.cursorrules, AGENTS.md, etc.)
            - SUSPICIOUS: unpredictable files needing human review
            Respond with JSON only.
        """
    }
}
