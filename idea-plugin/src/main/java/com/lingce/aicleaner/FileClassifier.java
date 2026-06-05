package com.lingce.aicleaner;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.Locale;
import java.util.Optional;

final class FileClassifier {
    private final OpenAiCompatibleClassifier aiClassifier = new OpenAiCompatibleClassifier();

    ClassificationResult classify(VirtualFile file, String preview, AiCleanerSettingsState settings) {
        ClassificationResult heuristic = classifyByRules(file, preview);
        if (!settings.isUseAiClassifier()) {
            return heuristic;
        }

        boolean shouldAskAi = heuristic.getCategory() == FileCategory.SUSPICIOUS
                || heuristic.getCategory() == FileCategory.UNKNOWN
                || heuristic.getCategory() == FileCategory.NORMAL
                || heuristic.getConfidence() < 0.92;
        if (!shouldAskAi) {
            return heuristic;
        }

        Optional<ClassificationResult> aiResult = aiClassifier.classify(file, preview, heuristic, settings);
        return aiResult.orElse(heuristic);
    }

    private ClassificationResult classifyByRules(VirtualFile file, String preview) {
        String path = normalized(file.getPath());
        String name = file.getName().toLowerCase(Locale.ROOT);
        String content = preview == null ? "" : preview.toLowerCase(Locale.ROOT);

        if (isInQuarantine(path)) {
            return normal("Already in quarantine");
        }

        if (isTemporaryFile(path, name)) {
            return new ClassificationResult(FileCategory.TEMP, 0.97, "Name or location matches a temporary-file pattern.");
        }

        if (isAiConfig(path, name)) {
            return new ClassificationResult(FileCategory.AI_CONFIG, 0.95, "Path matches common AI assistant configuration files.");
        }

        if (isProjectConfig(path, name)) {
            return new ClassificationResult(FileCategory.PROJECT_CONFIG, 0.92, "Path matches project or IDE configuration files.");
        }

        if (looksLikeGeneratedAiThrowaway(path, name, content)) {
            return new ClassificationResult(FileCategory.AI_GENERATED_USELESS, 0.88, "File has AI-generated markers and appears to be scratch output.");
        }

        if (looksSuspicious(path, name, content)) {
            return new ClassificationResult(FileCategory.SUSPICIOUS, 0.65, "File is unusual for project source and should be reviewed.");
        }

        return normal("No cleanup pattern matched.");
    }

    private static ClassificationResult normal(String reason) {
        return new ClassificationResult(FileCategory.NORMAL, 0.8, reason);
    }

    private static boolean isInQuarantine(String path) {
        return path.contains("/.ai-cleaner-quarantine/");
    }

    private static boolean isTemporaryFile(String path, String name) {
        return path.contains("/tmp/")
                || path.contains("/temp/")
                || path.contains("/.tmp/")
                || path.contains("/.temp/")
                || name.endsWith(".tmp")
                || name.endsWith(".temp")
                || name.endsWith(".swp")
                || name.endsWith(".swo")
                || name.endsWith(".bak")
                || name.endsWith(".orig")
                || name.endsWith(".rej")
                || name.endsWith("~")
                || name.startsWith(".#")
                || name.startsWith("#") && name.endsWith("#");
    }

    private static boolean isAiConfig(String path, String name) {
        return path.contains("/.cursor/")
                || path.contains("/.windsurf/")
                || path.contains("/.continue/")
                || path.contains("/.aider")
                || path.contains("/.claude/")
                || path.contains("/.github/copilot")
                || name.equals("claude.md")
                || name.equals("agents.md")
                || name.equals(".aider.conf.yml")
                || name.equals(".aiderignore")
                || name.equals(".cursorrules")
                || name.equals("cursor.json")
                || name.equals("mcp.json");
    }

    private static boolean isProjectConfig(String path, String name) {
        return path.contains("/.idea/")
                || path.contains("/.vscode/")
                || path.contains("/.gradle/")
                || path.contains("/target/")
                || path.contains("/build/")
                || path.contains("/dist/")
                || path.contains("/out/")
                || name.endsWith(".iml")
                || name.equals(".env")
                || name.equals(".env.local")
                || name.equals(".env.development")
                || name.equals(".env.test")
                || name.equals("workspace.xml")
                || name.equals("tasks.xml")
                || name.equals("launch.json")
                || name.equals("settings.json");
    }

    private static boolean looksLikeGeneratedAiThrowaway(String path, String name, String content) {
        boolean scratchLocation = path.contains("/scratch/")
                || path.contains("/ai-output/")
                || path.contains("/ai-generated/")
                || path.contains("/generated/")
                || path.contains("/prompts/");
        boolean scratchName = name.contains("chatgpt")
                || name.contains("copilot")
                || name.contains("ai-output")
                || name.contains("ai_generated")
                || name.contains("generated-by-ai")
                || name.startsWith("untitled")
                || name.startsWith("newfile");
        boolean marker = content.contains("generated by chatgpt")
                || content.contains("generated by ai")
                || content.contains("ai-generated")
                || content.contains("this file was generated by")
                || content.contains("do not edit: generated");
        return (scratchLocation || scratchName) && marker;
    }

    private static boolean looksSuspicious(String path, String name, String content) {
        if (name.equals(".env") || name.endsWith(".pem") || name.endsWith(".key")) {
            return true;
        }
        if (path.contains("/__pycache__/") || path.contains("/.pytest_cache/")) {
            return true;
        }
        if ((name.startsWith("tmp") || name.startsWith("temp")) && content.length() < 4096) {
            return true;
        }
        return content.contains("api_key=")
                || content.contains("apikey=")
                || content.contains("secret_key=")
                || content.contains("private key");
    }

    private static String normalized(String path) {
        return path.replace('\\', '/').toLowerCase(Locale.ROOT);
    }
}
