package com.lingce.cleaner.core;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtilCore;

import java.util.Locale;
import java.util.Set;

public final class LocalFileClassifier {
    private static final Set<String> TEMP_FILE_NAMES = Set.of(
        ".ds_store",
        "thumbs.db",
        "desktop.ini",
        ".eslintcache",
        ".stylelintcache"
    );

    private static final Set<String> CONFIG_DIR_NAMES = Set.of(
        ".idea",
        ".vscode",
        ".settings"
    );

    private static final Set<String> CONFIG_FILE_NAMES = Set.of(
        ".classpath",
        ".env",
        ".env.local",
        ".factorypath",
        ".project",
        "workspace.xml",
        "tasks.xml"
    );

    private static final Set<String> AI_CONFIG_DIR_NAMES = Set.of(
        ".cursor",
        ".claude",
        ".continue",
        ".windsurf",
        ".aider",
        ".copilot"
    );

    private static final Set<String> AI_CONFIG_FILE_NAMES = Set.of(
        ".aider.conf.yml",
        ".aider.conf.yaml",
        ".aiderignore",
        ".cursorignore",
        "cursor.json",
        "claude.md",
        "agents.md",
        "copilot-instructions.md"
    );

    public FileDecision classify(Project project, VirtualFile file, String contentSample) {
        String name = file.getName();
        String lowerName = name.toLowerCase(Locale.ROOT);
        String relativePath = relativePath(project, file).toLowerCase(Locale.ROOT);

        if (isTemporary(file, lowerName, relativePath)) {
            return FileDecision.of(FileCategory.TEMPORARY, 0.98, "Temporary/cache file pattern");
        }

        if (isAiConfig(lowerName, relativePath)) {
            return FileDecision.of(FileCategory.AI_CONFIG, 0.95, "AI tool configuration file or directory");
        }

        if (isProjectConfig(file, lowerName, relativePath)) {
            return FileDecision.of(FileCategory.PROJECT_CONFIG, 0.90, "IDE or project-local configuration");
        }

        if (looksLikeAiGeneratedThrowaway(lowerName, relativePath, contentSample)) {
            return FileDecision.of(FileCategory.AI_GENERATED_USELESS, 0.86, "Looks like an AI-generated scratch or disposable artifact");
        }

        if (looksSuspicious(lowerName, relativePath, contentSample)) {
            return FileDecision.of(FileCategory.SUSPICIOUS, 0.66, "Unrecognized generated-looking file");
        }

        return FileDecision.keep("No cleanup signal");
    }

    private boolean isTemporary(VirtualFile file, String lowerName, String relativePath) {
        if (TEMP_FILE_NAMES.contains(lowerName)) {
            return true;
        }
        if (lowerName.endsWith(".tmp")
            || lowerName.endsWith(".temp")
            || lowerName.endsWith(".bak")
            || lowerName.endsWith(".orig")
            || lowerName.endsWith(".rej")
            || lowerName.endsWith(".swp")
            || lowerName.endsWith(".swo")
            || lowerName.endsWith("~")) {
            return true;
        }
        return file.isDirectory()
            && (relativePath.endsWith("/__pycache__")
            || relativePath.endsWith("/.pytest_cache")
            || relativePath.endsWith("/.mypy_cache")
            || relativePath.endsWith("/.ruff_cache")
            || relativePath.endsWith("/.cache"));
    }

    private boolean isAiConfig(String lowerName, String relativePath) {
        if (AI_CONFIG_FILE_NAMES.contains(lowerName)) {
            return true;
        }
        for (String dir : AI_CONFIG_DIR_NAMES) {
            if (relativePath.equals(dir) || relativePath.contains("/" + dir + "/") || relativePath.startsWith(dir + "/")) {
                return true;
            }
        }
        return relativePath.contains("/.cursor/")
            || relativePath.contains("/.claude/")
            || relativePath.contains("/.continue/")
            || lowerName.contains("ai-config")
            || lowerName.contains("prompt-config");
    }

    private boolean isProjectConfig(VirtualFile file, String lowerName, String relativePath) {
        if (lowerName.endsWith(".iml") || lowerName.startsWith(".env.") || CONFIG_FILE_NAMES.contains(lowerName)) {
            return true;
        }
        for (String dir : CONFIG_DIR_NAMES) {
            if (relativePath.equals(dir) || relativePath.startsWith(dir + "/") || relativePath.contains("/" + dir + "/")) {
                return true;
            }
        }
        return file.isDirectory() && (lowerName.equals(".idea") || lowerName.equals(".vscode"));
    }

    private boolean looksLikeAiGeneratedThrowaway(String lowerName, String relativePath, String contentSample) {
        String lowerContent = contentSample == null ? "" : contentSample.toLowerCase(Locale.ROOT);
        boolean generatedName = lowerName.contains("ai-generated")
            || lowerName.contains("generated-by-ai")
            || lowerName.contains("chatgpt")
            || lowerName.contains("claude")
            || lowerName.startsWith("prompt-")
            || lowerName.startsWith("response-")
            || lowerName.startsWith("scratch-")
            || relativePath.contains("/ai-output/")
            || relativePath.contains("/ai-outputs/")
            || relativePath.contains("/generated/");
        boolean disposableContent = lowerContent.contains("generated by ai")
            || lowerContent.contains("generated by chatgpt")
            || lowerContent.contains("generated by claude")
            || lowerContent.contains("as an ai language model")
            || lowerContent.contains("todo: verify this generated")
            || lowerContent.contains("temporary ai draft");
        return generatedName && (isLooseDocument(lowerName) || disposableContent);
    }

    private boolean looksSuspicious(String lowerName, String relativePath, String contentSample) {
        String lowerContent = contentSample == null ? "" : contentSample.toLowerCase(Locale.ROOT);
        return (relativePath.contains("/tmp/") || relativePath.contains("/temp/"))
            && !lowerName.endsWith(".java")
            && !lowerName.endsWith(".kt")
            || lowerContent.contains("delete me")
            || lowerContent.contains("throwaway")
            || lowerContent.contains("scratch file");
    }

    private boolean isLooseDocument(String lowerName) {
        return lowerName.endsWith(".md")
            || lowerName.endsWith(".txt")
            || lowerName.endsWith(".json")
            || lowerName.endsWith(".yaml")
            || lowerName.endsWith(".yml")
            || lowerName.endsWith(".log");
    }

    private String relativePath(Project project, VirtualFile file) {
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) {
            return file.getPath();
        }
        String relative = VfsUtilCore.getRelativePath(file, baseDir, '/');
        return relative == null ? file.getPath() : relative;
    }
}
