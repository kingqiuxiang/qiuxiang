package com.lingce.aicleaner.core

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.lingce.aicleaner.model.ClassificationResult
import com.lingce.aicleaner.model.FileCategory
import com.lingce.aicleaner.settings.AiCleanerSettings

/**
 * 文件分类器：先用本地规则（快、零成本），不确定时再可选地调用 AI。
 */
class FileClassifier(
    private val project: Project,
    private val settings: AiCleanerSettings = AiCleanerSettings.getInstance(),
    private val aiClient: AiClient = AiClient(settings),
) {

    private val state get() = settings.state

    private val tmp get() = GlobMatcher.parsePatterns(state.tmpPatterns)
    private val aiConfig get() = GlobMatcher.parsePatterns(state.aiConfigPatterns)
    private val projectConfig get() = GlobMatcher.parsePatterns(state.projectConfigPatterns)
    private val aiGenNames get() = GlobMatcher.parsePatterns(state.aiGeneratedNamePatterns)
    private val markers get() = state.aiContentMarkers
        .split('\n', '\r').map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * @param allowAi 是否允许在本地规则不确定时调用 AI（批量扫描时可由调用方统一控制）。
     */
    fun classify(file: VirtualFile, allowAi: Boolean = true): ClassificationResult {
        val name = file.name
        val rel = relativePath(file)

        // 永不触碰的关键目录
        if (isProtected(rel, name)) {
            return ClassificationResult(file, FileCategory.NORMAL, "受保护路径，跳过", 1.0)
        }

        // 1) 临时文件
        if (GlobMatcher.matchesAny(tmp, name, rel)) {
            return ClassificationResult(file, FileCategory.TMP, "匹配临时文件规则", 0.95)
        }

        // 2) AI 工具配置
        if (GlobMatcher.matchesAny(aiConfig, name, rel)) {
            return ClassificationResult(file, FileCategory.AI_CONFIG, "匹配 AI 配置规则", 0.9)
        }

        // 3) 项目配置
        if (GlobMatcher.matchesAny(projectConfig, name, rel)) {
            return ClassificationResult(file, FileCategory.PROJECT_CONFIG, "匹配项目配置规则", 0.85)
        }

        // 目录若未命中以上规则则按正常处理（递归交给扫描器）
        if (file.isDirectory) {
            return ClassificationResult(file, FileCategory.NORMAL, "目录", 1.0)
        }

        // 4) AI 生成无用文件：文件名规则
        if (GlobMatcher.matchesAny(aiGenNames, name, rel)) {
            return ClassificationResult(file, FileCategory.AI_GENERATED_USELESS, "文件名疑似 AI/草稿产物", 0.7)
        }

        // 5) AI 生成无用文件：内容标记
        val content = readSnippet(file)
        if (content != null) {
            val hit = markers.firstOrNull { content.contains(it, ignoreCase = true) }
            if (hit != null) {
                return ClassificationResult(
                    file, FileCategory.AI_GENERATED_USELESS, "内容包含标记：\"$hit\"", 0.8,
                )
            }
        }

        // 6) 不确定 → 可选调用 AI
        if (allowAi && settings.isAiConfigured && content != null) {
            val verdict = aiClient.classify(rel, file.length, content)
            if (verdict != null) {
                return ClassificationResult(file, verdict.category, "AI 判定：${verdict.reason}", 0.75, byAi = true)
            }
        }

        // 7) 仍不确定 → 启发式判定可疑 / 正常
        return heuristicFallback(file, name, rel, content)
    }

    private fun heuristicFallback(
        file: VirtualFile,
        name: String,
        rel: String,
        content: String?,
    ): ClassificationResult {
        val ext = file.extension?.lowercase()

        // 已知源码/资源后缀 → 正常
        if (ext != null && ext in KNOWN_GOOD_EXT) {
            return ClassificationResult(file, FileCategory.NORMAL, "已知源码/资源类型", 0.9)
        }

        // 二进制 + 未知后缀 + 不在常见资源里 → 可疑
        val isBinary = content == null && file.length > 0
        val noExt = ext == null
        val randomLooking = looksRandom(file.nameWithoutExtension)

        if (isBinary && (noExt || ext !in KNOWN_GOOD_EXT)) {
            return ClassificationResult(file, FileCategory.SUSPICIOUS, "未知类型的二进制文件", 0.5)
        }
        if (randomLooking) {
            return ClassificationResult(file, FileCategory.SUSPICIOUS, "文件名疑似随机生成", 0.5)
        }

        return ClassificationResult(file, FileCategory.NORMAL, "未命中任何规则，按正常保留", 0.6)
    }

    private fun isProtected(rel: String, name: String): Boolean {
        val r = rel.replace('\\', '/')
        return PROTECTED_PREFIXES.any { r == it || r.startsWith("$it/") } ||
            name == ".gitignore" || name == ".gitkeep"
    }

    private fun relativePath(file: VirtualFile): String {
        val base = project.basePath ?: return file.name
        val baseVf = ProjectPaths.baseDir(project)
        return if (baseVf != null)
            VfsUtilCore.getRelativePath(file, baseVf) ?: file.path.removePrefix(base).trimStart('/')
        else file.path.removePrefix(base).trimStart('/')
    }

    /** 读取文本片段；非文本/过大返回 null。 */
    private fun readSnippet(file: VirtualFile): String? {
        if (file.isDirectory) return null
        if (file.length == 0L || file.length > MAX_READ_BYTES) return null
        return try {
            val bytes = file.contentsToByteArray()
            if (looksBinary(bytes)) null else String(bytes, file.charset ?: Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun looksBinary(bytes: ByteArray): Boolean {
        val sample = bytes.take(8000)
        if (sample.isEmpty()) return false
        var nonText = 0
        for (b in sample) {
            val v = b.toInt() and 0xFF
            if (v == 0) return true
            if (v < 0x09 || (v in 0x0E..0x1F)) nonText++
        }
        return nonText.toDouble() / sample.size > 0.30
    }

    private fun looksRandom(base: String): Boolean {
        if (base.length < 12) return false
        val digits = base.count { it.isDigit() }
        val hexish = base.all { it.isLetterOrDigit() } && base.any { it.isDigit() } && base.any { it.isLetter() }
        return hexish && digits.toDouble() / base.length > 0.4
    }

    companion object {
        private const val MAX_READ_BYTES = 512 * 1024L

        private val PROTECTED_PREFIXES = listOf(".git", "node_modules", ".gradle", "build", "dist", "out", "target")

        private val KNOWN_GOOD_EXT = setOf(
            // 源码
            "java", "kt", "kts", "scala", "groovy", "go", "rs", "py", "rb", "php", "c", "h",
            "cpp", "hpp", "cc", "cs", "swift", "m", "mm", "js", "jsx", "ts", "tsx", "vue",
            "svelte", "dart", "lua", "sh", "bash", "zsh", "sql", "r", "jl", "ex", "exs",
            // 标记/配置/文档
            "json", "yaml", "yml", "toml", "xml", "html", "htm", "css", "scss", "sass", "less",
            "md", "mdx", "txt", "rst", "adoc", "properties", "ini", "conf", "env", "gradle",
            "csv", "tsv", "proto", "graphql", "gql",
            // 资源
            "png", "jpg", "jpeg", "gif", "svg", "webp", "ico", "bmp", "woff", "woff2", "ttf",
            "otf", "eot", "mp3", "mp4", "wav", "pdf",
        )
    }
}
