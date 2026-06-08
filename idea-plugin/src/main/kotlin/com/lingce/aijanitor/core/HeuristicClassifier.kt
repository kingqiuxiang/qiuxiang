package com.lingce.aijanitor.core

import com.intellij.openapi.vfs.VirtualFile
import com.lingce.aijanitor.model.Classification
import com.lingce.aijanitor.model.CleanupAction
import com.lingce.aijanitor.model.FileCategory
import java.util.Locale

/**
 * Fast, offline classification based on file name / path / extension and a small
 * peek at the content. Used both as the only classifier (when AI is disabled)
 * and as the seed for the AI refinement pass.
 */
object HeuristicClassifier {

    private val TEMP_EXTENSIONS = setOf(
        "tmp", "temp", "bak", "old", "orig", "rej", "swp", "swo", "swn",
        "cache", "crdownload", "part", "partial", "download", "log", "logs",
        "pyc", "pyo", "class", "o", "obj", "lock~",
    )

    private val TEMP_NAMES = setOf(
        ".ds_store", "thumbs.db", "desktop.ini", "npm-debug.log", "yarn-error.log",
        "pnpm-debug.log", ".eslintcache", ".stylelintcache",
    )

    /** File name patterns (glob-ish) that indicate log/output files. */
    private val LOG_NAME_PATTERNS = listOf(
        Regex(".*\\.log\\.[0-9]+", RegexOption.IGNORE_CASE),   // log.1, log.20250101
        Regex(".*\\.log\\.[a-z]+", RegexOption.IGNORE_CASE),   // log.gz
        Regex(".*[-_]log\\.[a-z0-9]+", RegexOption.IGNORE_CASE), // error-log.txt
        Regex(".*\\.std(out|err)$", RegexOption.IGNORE_CASE),  // .stdout, .stderr
        Regex(".*\\.output$", RegexOption.IGNORE_CASE),        // .output
        Regex("catalina.*\\.log", RegexOption.IGNORE_CASE),    // catalina.out / catalina.log
        Regex(".*\\.(log|out|err)$", RegexOption.IGNORE_CASE), // any .log/.out/.err
    )

    /** Strong content patterns: a single hit is enough to flag a file as log output. */
    private val STRONG_LOG_MARKERS = listOf(
        "log level", "log-level", "stack trace", "stacktrace",
        "exception in thread", "at java.", "at org.", "caused by:",
    )

    /** Weak content patterns: need 3+ distinct hits to flag a file as log output.
     *  Individually these words (error:, info:, etc.) appear in normal docs/code
     *  describing error handling — a single match must never trigger TEMP. */
    private val WEAK_LOG_MARKERS = listOf(
        "error:", "warn:", "info:", "debug:", "trace:", "fatal:",
    )

    /** AI assistant / tooling configuration files. */
    private val AI_CONFIG_NAMES = setOf(
        ".cursorrules", ".cursorignore", ".cursorindexingignore",
        ".aider.conf.yml", ".aiderignore", ".aider.input.history", ".aider.chat.history.md",
        ".continuerc.json", ".windsurfrules", ".clinerules", ".roomodes",
        "claude.md", "agents.md", "gemini.md", "copilot-instructions.md",
        ".aiexclude", ".aiignore", ".codeiumignore", ".tabnine_root",
        ".specstory", ".coderabbit.yaml",
        ".agnix.toml", ".claudeignore",
    )

    private val AI_CONFIG_DIR_HINTS = setOf(
        ".cursor", ".continue", ".aider", ".codeium", ".tabnine", ".roo",
        ".github/copilot", ".specstory", ".claude", ".windsurf",
        ".ai", ".codebuddy", ".fastrequest", ".codegraph", ".ruler",
        ".cline", ".trae", ".qwen", ".lingma",
    )

    /** Directories that contain auto-generated data from AI/MCP tools — not project source. */
    private val TOOL_DATA_DIRS = setOf(
        ".playwright-mcp", ".playwright", "playwright-report", "test-results",
        ".mcp", ".mcp-servers",
    )

    /** File name patterns that look like tool output/results (not real project files). */
    private val TOOL_OUTPUT_NAME_PATTERNS = listOf(
        Regex(".*[-_]results\\.(json|yml|yaml|xml|csv)$", RegexOption.IGNORE_CASE),
        Regex(".*[-_]exploration\\.(json|yml|yaml|xml|csv)$", RegexOption.IGNORE_CASE),
        Regex(".*[-_]summary\\.(json|yml|yaml|xml)$", RegexOption.IGNORE_CASE),
        Regex(".*[-_]reference\\.(json|yml|yaml|xml)$", RegexOption.IGNORE_CASE),
        Regex("proxy[-_]resp\\.(json|yml|yaml)$", RegexOption.IGNORE_CASE),
        Regex("explore[-_]calls\\.(json|yml|yaml)$", RegexOption.IGNORE_CASE),
        Regex("agnix-(out|err)\\.(json|txt)$", RegexOption.IGNORE_CASE),
        Regex("api[-_]params[-_]reference\\.(json|yml|yaml)$", RegexOption.IGNORE_CASE),
        Regex("api[-_]exploration[-_]summary\\.(json|yml|yaml)$", RegexOption.IGNORE_CASE),
    )

    /** Regular project / tooling configuration files. */
    private val PROJECT_CONFIG_NAMES = setOf(
        ".editorconfig", ".gitattributes", ".gitignore", ".dockerignore",
        ".prettierrc", ".prettierignore", ".eslintignore", ".babelrc",
        ".npmrc", ".nvmrc", ".yarnrc", ".browserslistrc", ".env.example",
        "tsconfig.json", "jsconfig.json", ".prettierrc.json", ".eslintrc",
        ".eslintrc.json", ".eslintrc.js", ".eslintrc.cjs", ".stylelintrc",
        ".prettierrc.js", ".prettierrc.cjs",
    )

    private val PROJECT_CONFIG_PREFIXES = listOf("tsconfig.", ".eslintrc.", ".prettierrc.", ".stylelintrc.")

    /** Names that strongly suggest a throw-away / AI scratch file. */
    private val JUNK_NAME_REGEXES = listOf(
        Regex(".*\\.generated\\..*", RegexOption.IGNORE_CASE),
        Regex(".*[_-]generated\\..*", RegexOption.IGNORE_CASE),
        Regex("untitled.*", RegexOption.IGNORE_CASE),
        Regex("new[ _-]?file.*", RegexOption.IGNORE_CASE),
        Regex("scratch[_-].*", RegexOption.IGNORE_CASE),
        Regex("copy of .*", RegexOption.IGNORE_CASE),
        Regex(".* \\(copy\\).*", RegexOption.IGNORE_CASE),
        Regex(".*\\([0-9]+\\)\\.[a-z0-9]+", RegexOption.IGNORE_CASE),
        Regex("(test|tmp|temp|demo|example|sample)_?[0-9]+\\..*", RegexOption.IGNORE_CASE),
    )

    /** Content markers commonly emitted by AI tools / code generators. */
    private val AI_CONTENT_MARKERS = listOf(
        "generated by ai", "ai-generated", "ai generated",
        "this file was automatically generated", "this file is auto-generated",
        "automatically generated", "do not edit this file", "@generated",
        "generated by chatgpt", "generated by copilot", "generated by claude",
        "generated by cursor", "as an ai language model", "code generated by",
    )

    private val SOURCE_EXTENSIONS = setOf(
        "java", "kt", "kts", "scala", "groovy", "py", "rb", "go", "rs", "c", "h",
        "cpp", "hpp", "cc", "cs", "swift", "m", "mm", "js", "jsx", "ts", "tsx",
        "vue", "svelte", "php", "html", "htm", "css", "scss", "sass", "less",
        "json", "yaml", "yml", "xml", "md", "txt", "sql", "sh", "bash", "gradle",
        "toml", "ini", "properties", "dart", "lua", "r", "ipynb", "proto",
    )

    fun classify(
        file: VirtualFile,
        relativePath: String,
        contentSnippet: String?,
        extraTempPatterns: List<String>,
        aiKeepPatterns: List<String>,
    ): Classification {
        val name = file.name.lowercase(Locale.ROOT)
        val ext = file.extension?.lowercase(Locale.ROOT).orEmpty()
        val path = relativePath.lowercase(Locale.ROOT).replace('\\', '/')

        // 0) Log / output files (check before AI config to prevent misclassification).
        val isLogName = ext == "log" || ext == "logs" ||
            LOG_NAME_PATTERNS.any { it.matches(name) } ||
            LOG_NAME_PATTERNS.any { it.matches(relativePath) }
        val isLogContent = contentSnippet != null && isStrongLogContent(contentSnippet)
        if (isLogName || isLogContent) {
            val reason = if (isLogContent) "文件内容包含日志特征" else "日志/输出文件"
            return Classification(FileCategory.TEMP, reason, CleanupAction.DELETE, selectedByDefault = true)
        }

        // 0.5) Tool data / MCP cache directories — auto-generated, should be git-excluded.
        val inToolDataDir = TOOL_DATA_DIRS.any { d ->
            path == d || path.startsWith("$d/")
        }
        if (inToolDataDir) {
            return Classification(FileCategory.TEMP, "AI 工具/MCP 自动生成的数据目录", CleanupAction.IGNORE, selectedByDefault = true)
        }

        // 0.6) Tool output files by name pattern (e.g. kyc-results.json, proxy-resp.json).
        if (TOOL_OUTPUT_NAME_PATTERNS.any { it.matches(name) || it.matches(relativePath) }) {
            return Classification(FileCategory.TEMP, "疑似工具/脚本输出的临时文件", CleanupAction.DELETE, selectedByDefault = true)
        }

        // 1) AI config (highest priority so we never delete them).
        val builtinAiConfig = name in AI_CONFIG_NAMES || AI_CONFIG_DIR_HINTS.any { path.contains("$it/") || path.startsWith("$it/") }
        val userAiKeep = aiKeepPatterns.any { matchesGlob(file.name, it) }
        if (builtinAiConfig || userAiKeep) {
            val reason = if (userAiKeep) "AI 工具所需配置文件（用户规则）" else "AI 工具配置文件"
            return Classification(FileCategory.AI_CONFIG, reason, CleanupAction.IGNORE, selectedByDefault = false)
        }

        // 2) Project config.
        if (name in PROJECT_CONFIG_NAMES || PROJECT_CONFIG_PREFIXES.any { name.startsWith(it) }) {
            return Classification(FileCategory.PROJECT_CONFIG, "项目/工具链配置文件", CleanupAction.IGNORE, selectedByDefault = false)
        }

        // 3) Temp / junk by extension, name, path or custom pattern.
        val inTempDir = listOf("/tmp/", "/temp/", "/.cache/", "/.tmp/", "__pycache__/", "/.pytest_cache/")
            .any { path.contains(it) } || path.startsWith("tmp/") || path.startsWith("temp/")
        val customTemp = extraTempPatterns.any { matchesGlob(name, it) }
        if (ext in TEMP_EXTENSIONS || name in TEMP_NAMES || name.endsWith("~") || inTempDir || customTemp) {
            return Classification(FileCategory.TEMP, "临时/缓存文件，可安全清理", CleanupAction.DELETE, selectedByDefault = true)
        }

        // 4) AI generated junk by name.
        if (JUNK_NAME_REGEXES.any { it.matches(name) }) {
            return Classification(FileCategory.AI_JUNK, "文件名疑似自动/AI 生成的临时产物", CleanupAction.DELETE, selectedByDefault = true)
        }

        // 5) AI generated junk by content marker.
        if (contentSnippet != null) {
            val lower = contentSnippet.lowercase(Locale.ROOT)
            val marker = AI_CONTENT_MARKERS.firstOrNull { lower.contains(it) }
            if (marker != null) {
                return Classification(FileCategory.AI_JUNK, "内容包含自动生成标记：\"$marker\"", CleanupAction.DELETE, selectedByDefault = false)
            }
        }

        // 6) Recognized source / doc → normal.
        if (ext in SOURCE_EXTENSIONS) {
            return Classification(FileCategory.NORMAL, "常规源码/文档文件", CleanupAction.KEEP, selectedByDefault = false)
        }

        // 7) Everything else is unknown / suspicious.
        val reason = if (ext.isBlank()) "无扩展名的未知文件" else "未识别的文件类型 .$ext"
        return Classification(FileCategory.SUSPICIOUS, reason, CleanupAction.ARCHIVE, selectedByDefault = false)
    }

    /**
     * Returns true when [snippet] strongly resembles log output.
     * A single strong marker (stack trace, "exception in thread", etc.) is enough,
     * or 3+ distinct weak markers ("error:", "warn:", …) appearing together.
     * This avoids false positives on docs that mention "error:" in API descriptions.
     */
    private fun isStrongLogContent(snippet: String): Boolean {
        val lower = snippet.lowercase(Locale.ROOT)
        if (STRONG_LOG_MARKERS.any { lower.contains(it) }) return true
        val weakHits = WEAK_LOG_MARKERS.count { lower.contains(it) }
        return weakHits >= 3
    }

    private fun matchesGlob(name: String, glob: String): Boolean {
        if (glob.isBlank()) return false
        val regex = Regex(
            "^" + Regex.escape(glob.lowercase(Locale.ROOT))
                .replace("\\*", ".*")
                .replace("\\?", ".") + "$"
        )
        return regex.matches(name)
    }
}
