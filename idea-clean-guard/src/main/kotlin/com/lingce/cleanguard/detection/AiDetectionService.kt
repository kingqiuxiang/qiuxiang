package com.lingce.cleanguard.detection

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.diagnostic.Logger
import com.lingce.cleanguard.model.ClassifiedFile
import com.lingce.cleanguard.model.FileCategory
import com.lingce.cleanguard.settings.CleanGuardSettings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AiDetectionService {

    private val log = Logger.getInstance(AiDetectionService::class.java)
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun classifyWithAi(
        relativePath: String,
        contentPreview: String,
    ): ClassifiedFile? {
        val settings = CleanGuardSettings.getInstance().state
        if (!settings.useAiDetection || settings.apiKey.isBlank()) {
            return null
        }

        val prompt = """
            你是代码仓库文件分类助手。根据文件路径和内容片段，判断文件类别。
            只返回 JSON，不要 markdown：
            {"category":"TMP|AI_GENERATED_USELESS|AI_CONFIG|PROJECT_CONFIG|SUSPICIOUS|SAFE","reason":"简短中文原因","confidence":0.0-1.0}

            规则：
            - TMP: 临时/备份/编辑器残留
            - AI_GENERATED_USELESS: AI 生成且对项目无实际价值（脚手架、重复示例、无用草稿）
            - AI_CONFIG: Cursor/Copilot/Continue 等 AI 工具配置
            - PROJECT_CONFIG: .env、本地密钥、IDE 工作区状态等应 ignore 的配置
            - SUSPICIOUS: 无法确定、来源可疑
            - SAFE: 正常项目源码或资源

            文件路径: $relativePath
            内容片段:
            ${contentPreview.take(settings.maxAiFileBytes)}
        """.trimIndent()

        return try {
            val body = JsonObject().apply {
                addProperty("model", settings.model)
                add("messages", gson.toJsonTree(listOf(
                    mapOf("role" to "user", "content" to prompt),
                )))
                addProperty("temperature", 0.1)
            }

            val request = Request.Builder()
                .url("${settings.baseUrl}/chat/completions")
                .addHeader("Authorization", "Bearer ${settings.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    log.warn("AI detection failed: HTTP ${response.code}")
                    return null
                }
                val responseBody = response.body?.string() ?: return null
                parseAiResponse(relativePath, responseBody)
            }
        } catch (e: Exception) {
            log.warn("AI detection error", e)
            null
        }
    }

    private fun parseAiResponse(relativePath: String, responseBody: String): ClassifiedFile? {
        return try {
            val root = gson.fromJson(responseBody, JsonObject::class.java)
            val content = root.getAsJsonArray("choices")
                ?.get(0)?.asJsonObject
                ?.getAsJsonObject("message")
                ?.get("content")?.asString ?: return null

            val jsonStart = content.indexOf('{')
            val jsonEnd = content.lastIndexOf('}')
            if (jsonStart < 0 || jsonEnd <= jsonStart) return null

            val result = gson.fromJson(content.substring(jsonStart, jsonEnd + 1), JsonObject::class.java)
            val categoryName = result.get("category")?.asString ?: return null
            val reason = result.get("reason")?.asString ?: "AI 分类"
            val confidence = result.get("confidence")?.asDouble ?: 0.75

            val category = runCatching {
                FileCategory.valueOf(categoryName.trim().uppercase())
            }.getOrDefault(FileCategory.SUSPICIOUS)

            ClassifiedFile(relativePath, category, reason, confidence)
        } catch (e: Exception) {
            log.warn("Failed to parse AI response", e)
            null
        }
    }
}
