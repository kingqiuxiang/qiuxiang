package com.lingce.aijanitor.core

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.lingce.aijanitor.model.CleanupAction
import com.lingce.aijanitor.model.FileCategory
import com.lingce.aijanitor.model.ScanItem

/**
 * Refines heuristic results by asking an AI model to classify each candidate file.
 * The model only receives file metadata and a short content snippet.
 */
class AiClassifier(private val client: AiClient, private val batchSize: Int) {

    private val systemPrompt = """
        你是一个严格的代码仓库清理助手。你会收到若干文件的元数据和内容片段，需要判断每个文件属于以下哪一类：
        - ai_junk: AI 工具生成的、对项目无用的临时产物（例如对话残留、示例脚手架、明显是 AI 写完忘删的草稿）。
        - temp: 临时文件、缓存、构建产物、编辑器/系统垃圾文件。
        - project_config: 项目或工具链配置文件（如 tsconfig.json、.editorconfig 等）。
        - ai_config: AI 编程工具的配置文件（如 .cursorrules、CLAUDE.md、.aider* 等）。
        - suspicious: 无法确定用途、可疑或异常的文件。
        - normal: 正常的源码、文档或资源文件，应当保留。
        判断"是否有用"时要保守：只有当你确信文件对项目无用时才归为 ai_junk。
        必须只返回一个 JSON 数组，不要任何额外文字。数组每个元素形如：
        {"index": <int>, "category": "<上面之一>", "useful": <true|false>, "reason": "<不超过30字的中文理由>"}
    """.trimIndent()

    /** Mutates [items] in place with AI-derived categories/reasons. */
    fun refine(items: List<ScanItem>, snippets: Map<String, String>, indicator: ProgressIndicator?) {
        val candidates = items.filter { it.category != FileCategory.NORMAL || true } // classify all
        candidates.chunked(batchSize.coerceAtLeast(1)).forEachIndexed { chunkIdx, chunk ->
            indicator?.checkCanceled()
            indicator?.text2 = "AI 分析中 (${chunkIdx * batchSize + 1}..${chunkIdx * batchSize + chunk.size})"
            try {
                val userPrompt = buildPrompt(chunk, snippets)
                val content = client.chat(systemPrompt, userPrompt)
                applyResponse(content, chunk)
            } catch (e: Exception) {
                LOG.warn("AI 分类失败，保留启发式结果: ${e.message}")
            }
        }
    }

    private fun buildPrompt(chunk: List<ScanItem>, snippets: Map<String, String>): String {
        val sb = StringBuilder("请分类以下 ${chunk.size} 个文件：\n\n")
        chunk.forEachIndexed { i, item ->
            val snippet = snippets[item.relativePath]?.take(800)?.replace("```", "ˋˋˋ") ?: "(二进制或空文件)"
            sb.append("### index=$i\n")
            sb.append("path: ${item.relativePath}\n")
            sb.append("heuristic: ${item.category.name.lowercase()}\n")
            sb.append("content:\n```\n$snippet\n```\n\n")
        }
        return sb.toString()
    }

    private fun applyResponse(content: String, chunk: List<ScanItem>) {
        val json = extractJsonArray(content) ?: return
        for (el in json) {
            val obj = el.asJsonObject
            val index = obj.get("index")?.asInt ?: continue
            if (index !in chunk.indices) continue
            val item = chunk[index]
            val category = parseCategory(obj.get("category")?.asString) ?: item.category
            val useful = obj.get("useful")?.asBoolean ?: true
            val reason = obj.get("reason")?.asString?.takeIf { it.isNotBlank() } ?: item.reason

            var finalCategory = category
            if (category == FileCategory.NORMAL && !useful) {
                finalCategory = FileCategory.AI_JUNK
            }
            item.category = finalCategory
            item.reason = "AI：$reason"
            item.action = defaultActionFor(finalCategory)
            item.selected = finalCategory == FileCategory.AI_JUNK || finalCategory == FileCategory.TEMP
        }
    }

    private fun parseCategory(value: String?): FileCategory? = when (value?.lowercase()?.trim()) {
        "ai_junk", "junk" -> FileCategory.AI_JUNK
        "temp", "tmp" -> FileCategory.TEMP
        "project_config" -> FileCategory.PROJECT_CONFIG
        "ai_config" -> FileCategory.AI_CONFIG
        "suspicious" -> FileCategory.SUSPICIOUS
        "normal" -> FileCategory.NORMAL
        else -> null
    }

    private fun defaultActionFor(category: FileCategory): CleanupAction = when (category) {
        FileCategory.AI_JUNK, FileCategory.TEMP -> CleanupAction.DELETE
        FileCategory.PROJECT_CONFIG, FileCategory.AI_CONFIG -> CleanupAction.IGNORE
        FileCategory.SUSPICIOUS -> CleanupAction.ARCHIVE
        FileCategory.NORMAL -> CleanupAction.KEEP
    }

    private fun extractJsonArray(content: String): JsonArray? {
        val start = content.indexOf('[')
        val end = content.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        return try {
            JsonParser.parseString(content.substring(start, end + 1)).asJsonArray
        } catch (e: Exception) {
            LOG.warn("无法解析 AI 返回的 JSON: ${e.message}")
            null
        }
    }

    companion object {
        private val LOG = logger<AiClassifier>()
    }
}
