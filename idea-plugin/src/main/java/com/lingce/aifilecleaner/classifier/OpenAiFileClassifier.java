package com.lingce.aifilecleaner.classifier;

import com.lingce.aifilecleaner.settings.AiFileCleanerSettings;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OpenAiFileClassifier implements FileClassifier {
    private static final Pattern CONTENT_PATTERN = Pattern.compile("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.DOTALL);
    private static final Pattern KIND_PATTERN = Pattern.compile("\"kind\"\\s*:\\s*\"([A-Z_]+)\"");
    private static final Pattern ACTION_PATTERN = Pattern.compile("\"action\"\\s*:\\s*\"([A-Z_]+)\"");
    private static final Pattern CONFIDENCE_PATTERN = Pattern.compile("\"confidence\"\\s*:\\s*([0-9.]+)");
    private static final Pattern REASON_PATTERN = Pattern.compile("\"reason\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private final HttpClient client;
    private final HeuristicFileClassifier fallback;

    public OpenAiFileClassifier() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build(), new HeuristicFileClassifier());
    }

    OpenAiFileClassifier(HttpClient client, HeuristicFileClassifier fallback) {
        this.client = client;
        this.fallback = fallback;
    }

    @Override
    public FileClassification classify(FileSample sample) {
        AiFileCleanerSettings settings = AiFileCleanerSettings.getInstance();
        AiFileCleanerSettings.PluginState state = settings.getState();
        String apiKey = settings.getApiKey();
        if (!state.useAiClassifier || apiKey == null || apiKey.isBlank() || sample.binary()) {
            return fallback.classify(sample);
        }

        FileClassification heuristic = fallback.classify(sample);
        if (heuristic.kind() == FileClassification.Kind.SAFE
                || heuristic.kind() == FileClassification.Kind.TEMPORARY
                || heuristic.kind() == FileClassification.Kind.PROJECT_CONFIG
                || heuristic.kind() == FileClassification.Kind.AI_CONFIG) {
            return heuristic;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(resolveChatCompletionsUri(state.baseUrl))
                    .timeout(Duration.ofSeconds(18))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(state.model, sample, heuristic)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return annotateFallback(heuristic, "AI classifier failed with HTTP " + response.statusCode());
            }
            return parseClassification(response.body(), heuristic);
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return annotateFallback(heuristic, "AI classifier unavailable: " + error.getMessage());
        }
    }

    private static URI resolveChatCompletionsUri(String baseUrl) {
        String normalized = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/chat/completions")) {
            return URI.create(normalized);
        }
        return URI.create(normalized + "/chat/completions");
    }

    private static String buildRequestBody(String model, FileSample sample, FileClassification heuristic) {
        String systemPrompt = """
                You classify local IDE project files for safe cleanup.
                Return only compact JSON with:
                {"kind":"SAFE|TEMPORARY|AI_GENERATED|PROJECT_CONFIG|AI_CONFIG|SUSPICIOUS","action":"NONE|DELETE|IGNORE_EXCLUDE|ASK","confidence":0.0-1.0,"reason":"short reason"}
                Prefer ASK for source code or unclear files. DELETE only for obvious temporary or useless AI scratch artifacts.
                IGNORE_EXCLUDE is only for IDE/project/AI assistant configuration files that should be kept out of source control or excluded from the IDE.
                """;
        String userPrompt = "Relative path: " + sample.relativePath() + "\n"
                + "File name: " + sample.fileName() + "\n"
                + "Directory: " + sample.directory() + "\n"
                + "Size bytes: " + sample.sizeBytes() + "\n"
                + "Heuristic: " + heuristic.kind() + " / " + heuristic.recommendedAction() + " / " + heuristic.reason() + "\n"
                + "Content sample:\n" + sample.contentSnippet();

        return "{"
                + "\"model\":\"" + escapeJson(model == null || model.isBlank() ? "gpt-4o-mini" : model.trim()) + "\","
                + "\"temperature\":0,"
                + "\"response_format\":{\"type\":\"json_object\"},"
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":\"" + escapeJson(systemPrompt) + "\"},"
                + "{\"role\":\"user\",\"content\":\"" + escapeJson(userPrompt) + "\"}"
                + "]"
                + "}";
    }

    private static FileClassification parseClassification(String responseBody, FileClassification fallback) {
        Matcher contentMatcher = CONTENT_PATTERN.matcher(responseBody);
        if (!contentMatcher.find()) {
            return annotateFallback(fallback, "AI classifier response did not include message content.");
        }
        String content = unescapeJson(contentMatcher.group(1));
        FileClassification.Kind kind = parseEnum(KIND_PATTERN, content, FileClassification.Kind.class, fallback.kind());
        FileClassification.RecommendedAction action = parseEnum(
                ACTION_PATTERN,
                content,
                FileClassification.RecommendedAction.class,
                fallback.recommendedAction()
        );
        double confidence = parseConfidence(content, fallback.confidence());
        String reason = parseReason(content, fallback.reason());
        return new FileClassification(kind, action, confidence, reason, "ai");
    }

    private static <E extends Enum<E>> E parseEnum(Pattern pattern, String content, Class<E> enumType, E fallback) {
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumType, matcher.group(1).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static double parseConfidence(String content, double fallback) {
        Matcher matcher = CONFIDENCE_PATTERN.matcher(content);
        if (!matcher.find()) {
            return fallback;
        }
        try {
            double confidence = Double.parseDouble(matcher.group(1));
            return Math.max(0.0, Math.min(1.0, confidence));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String parseReason(String content, String fallback) {
        Matcher matcher = REASON_PATTERN.matcher(content);
        if (!matcher.find()) {
            return fallback;
        }
        return unescapeJson(matcher.group(1));
    }

    private static FileClassification annotateFallback(FileClassification fallback, String note) {
        return new FileClassification(
                fallback.kind(),
                fallback.recommendedAction(),
                fallback.confidence(),
                fallback.reason() + " (" + note + ")",
                fallback.source()
        );
    }

    private static String escapeJson(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.toString();
    }

    private static String unescapeJson(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 >= value.length()) {
                builder.append(c);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case '\\' -> builder.append('\\');
                case '"' -> builder.append('"');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case 'u' -> {
                    if (i + 4 < value.length()) {
                        String hex = value.substring(i + 1, i + 5);
                        try {
                            builder.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        } catch (NumberFormatException ignored) {
                            builder.append("\\u").append(hex);
                            i += 4;
                        }
                    } else {
                        builder.append("\\u");
                    }
                }
                default -> builder.append(next);
            }
        }
        return builder.toString();
    }
}
