package io.lingce.ideaguardian.service

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import io.lingce.ideaguardian.model.FileCategory
import io.lingce.ideaguardian.model.ScanItem
import io.lingce.ideaguardian.model.SuggestedAction
import io.lingce.ideaguardian.settings.AiFileGuardianSettingsState
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.asSequence

class ProjectFileScanner(private val settings: AiFileGuardianSettingsState) {
    private val aiClassifier = RemoteAiClassifier(settings)
    private var apiBudget = settings.maxApiDetectionsPerScan.coerceAtLeast(0)

    fun scan(project: Project, indicator: ProgressIndicator): List<ScanItem> {
        val basePath = project.basePath?.let(Path::of) ?: return emptyList()
        val result = mutableListOf<ScanItem>()

        Files.walk(basePath).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() }
                .filterNot { shouldSkipPath(basePath, it) }
                .forEach { file ->
                    indicator.checkCanceled()
                    indicator.text2 = file.toString()
                    classify(basePath, file)?.let(result::add)
                }
        }
        return result
    }

    private fun classify(basePath: Path, file: Path): ScanItem? {
        val relative = basePath.relativize(file).toString().replace('\\', '/')
        val lowerRel = relative.lowercase(Locale.ROOT)
        val lowerName = file.name.lowercase(Locale.ROOT)

        if (isTmpFile(lowerRel, lowerName, file.extension.lowercase(Locale.ROOT))) {
            return ScanItem(file, relative, FileCategory.TMP, SuggestedAction.DELETE, "临时文件/缓存文件")
        }

        if (isConfigOrAiConfig(lowerRel, lowerName)) {
            return ScanItem(
                file,
                relative,
                FileCategory.CONFIG_OR_AI_CONFIG,
                SuggestedAction.ADD_TO_IGNORE_EXCLUDE,
                "项目配置或 AI 配置文件，建议加入 ignore/exclude"
            )
        }

        val content = safeRead(file)
        val aiGenerated = isLikelyAiGenerated(lowerName, content, relative)
        val useless = isLikelyUseless(lowerName, content)
        if (aiGenerated && useless) {
            return ScanItem(
                file,
                relative,
                FileCategory.AI_USELESS,
                SuggestedAction.DELETE,
                "识别为 AI 生成且疑似无用文件"
            )
        }

        if (isSuspicious(file, lowerRel, lowerName, content)) {
            return ScanItem(
                file,
                relative,
                FileCategory.SUSPICIOUS,
                SuggestedAction.ARCHIVE_OR_DELETE,
                "可疑文件，建议一键转存或删除"
            )
        }

        return null
    }

    private fun shouldSkipPath(basePath: Path, path: Path): Boolean {
        val rel = basePath.relativize(path).toString().replace('\\', '/').lowercase(Locale.ROOT)
        val ignoredDirs = listOf(
            ".git/",
            "node_modules/",
            "build/",
            "dist/",
            "out/",
            "target/",
            ".gradle/",
            ".idea-file-guardian/archive/"
        )
        return ignoredDirs.any { rel.startsWith(it) || rel.contains("/$it") }
    }

    private fun isTmpFile(relative: String, lowerName: String, ext: String): Boolean {
        val tmpExt = setOf("tmp", "temp", "bak", "old", "orig", "swp", "swo", "cache", "log")
        if (ext in tmpExt) return true
        if (lowerName.endsWith("~") || lowerName.startsWith(".tmp")) return true
        if (relative.contains("/tmp/") || relative.contains("/temp/")) return true
        return false
    }

    private fun isConfigOrAiConfig(relative: String, lowerName: String): Boolean {
        val configNames = setOf(
            "package.json",
            "package-lock.json",
            "pnpm-lock.yaml",
            "yarn.lock",
            "pom.xml",
            "build.gradle",
            "build.gradle.kts",
            "settings.gradle",
            "settings.gradle.kts",
            "gradle.properties",
            ".env",
            ".env.local",
            ".gitignore",
            ".editorconfig",
            "tsconfig.json",
            "vite.config.ts",
            "vite.config.js",
            "webpack.config.js",
            "docker-compose.yml",
            "dockerfile",
            "plugin.xml"
        )
        if (lowerName in configNames) return true

        val aiConfigNames = setOf(
            ".cursorrules",
            "agents.md",
            "copilot-instructions.md",
            ".aider.conf.yml",
            ".aider.conf.yaml",
            "claude.md"
        )
        if (lowerName in aiConfigNames) return true

        val configDirPrefixes = listOf(".idea/", ".vscode/", ".cursor/", ".github/")
        return configDirPrefixes.any { relative.startsWith(it) }
    }

    private fun isLikelyAiGenerated(lowerName: String, content: String?, relativePath: String): Boolean {
        val nameHints = listOf("generated", "autogen", "chatgpt", "copilot", "cursor", "assistant", "llm")
        val contentHints = listOf(
            "generated by",
            "ai-generated",
            "this file was generated by",
            "chatgpt",
            "copilot",
            "cursor",
            "claude"
        )
        val byHeuristic = nameHints.any { lowerName.contains(it) } ||
            (content != null && contentHints.any { content.lowercase(Locale.ROOT).contains(it) })
        if (byHeuristic) return true

        val snippet = content?.take(1800)
        if (snippet.isNullOrBlank() || apiBudget <= 0) return false
        apiBudget -= 1
        return aiClassifier.classify(relativePath, snippet) ?: false
    }

    private fun isLikelyUseless(lowerName: String, content: String?): Boolean {
        val uselessHints = listOf("copy", "backup", "draft", "unused", "scratch", "sample-output", "test-output")
        if (uselessHints.any { lowerName.contains(it) }) {
            return true
        }
        if (content == null) return false
        val text = content.lowercase(Locale.ROOT)
        return text.contains("for demo only") || text.contains("placeholder only") || text.contains("todo: remove")
    }

    private fun isSuspicious(file: Path, relative: String, lowerName: String, content: String?): Boolean {
        val suspiciousExt = setOf("exe", "dll", "bat", "cmd", "ps1", "jar", "bin")
        val ext = file.extension.lowercase(Locale.ROOT)
        if (ext in suspiciousExt) return true

        val randomName = Regex("^[a-f0-9]{20,}(\\.[a-z0-9]+)?$")
        if (randomName.matches(lowerName)) return true

        if (file.fileSize() > 8 * 1024 * 1024 && !relative.contains("/assets/")) return true

        if (content != null) {
            val lowered = content.lowercase(Locale.ROOT)
            if (lowered.contains("curl http") && lowered.contains("| bash")) return true
            if (lowered.contains("powershell -encodedcommand")) return true
        }
        return false
    }

    private fun safeRead(file: Path): String? {
        return try {
            if (file.fileSize() > 128 * 1024) return null
            val bytes = Files.readAllBytes(file)
            String(bytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
