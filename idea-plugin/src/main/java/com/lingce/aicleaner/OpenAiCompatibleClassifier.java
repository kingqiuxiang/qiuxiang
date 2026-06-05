package com.lingce.aicleaner;

import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OpenAiCompatibleClassifier {
    private static final Pattern CONTENT_PATTERN = Pattern.compile("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("\"category\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONFIDENCE_PATTERN = Pattern.compile("\"confidence\"\\s*:\\s*([0-9.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REASON_PATTERN = Pattern.compile("\"reason\"\\s*:\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    Optional<ClassificationResult> classify(
            VirtualFile file,
            String preview,
            ClassificationResult heuristic,
            AiCleanerSettingsState settings
    ) {
        String requestBody = requestBody(file, preview, heuristic, settings);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(settings.getBaseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + settings.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            return parseResponse(response.body());
        } catch (IOException | InterruptedException | RuntimeException ignored) {
            if (ignored instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    private static String requestBody(
            VirtualFile file,
            String preview,
            ClassificationResult heuristic,
            AiCleanerSettingsState settings
    ) {
        String system = """
                You classify local IDE project files for cleanup. Return JSON only:
                {"category":"TEMP|AI_GENERATED_USELESS|PROJECT_CONFIG|AI_CONFIG|SUSPICIOUS|NORMAL|UNKNOWN","confidence":0.0-1.0,"reason":"short reason"}.
                Prefer NORMAL when uncertain. Mark AI_GENERATED_USELESS only when it is clearly throwaway generated output.
                Mark PROJECT_CONFIG or AI_CONFIG for IDE/tool/AI-assistant configuration that should usually be ignored.
                """;
        String user = "Path: " + file.getPath()
                + "\nName: " + file.getName()
                + "\nHeuristic: " + heuristic
                + "\nContent preview:\n" + safePreview(preview, settings.getMaxPreviewBytes());
        return "{"
                + "\"model\":\"" + json(settings.getModel()) + "\","
                + "\"temperature\":0,"
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":\"" + json(system) + "\"},"
                + "{\"role\":\"user\",\"content\":\"" + json(user) + "\"}"
                + "],"
                + "\"response_format\":{\"type\":\"json_object\"}"
                + "}";
    }

    private static Optional<ClassificationResult> parseResponse(String body) {
        Matcher contentMatcher = CONTENT_PATTERN.matcher(body);
        if (!contentMatcher.find()) {
            return Optional.empty();
        }

        String content = unescapeJson(contentMatcher.group(1));
        Matcher categoryMatcher = CATEGORY_PATTERN.matcher(content);
        if (!categoryMatcher.find()) {
            return Optional.empty();
        }

        FileCategory category = parseCategory(categoryMatcher.group(1));
        double confidence = parseConfidence(content);
        String reason = parseReason(content);
        return Optional.of(new ClassificationResult(category, confidence, reason));
    }

    private static FileCategory parseCategory(String raw) {
        String normalized = raw.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        try {
            return FileCategory.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return FileCategory.UNKNOWN;
        }
    }

    private static double parseConfidence(String content) {
        Matcher matcher = CONFIDENCE_PATTERN.matcher(content);
        if (!matcher.find()) {
            return 0.5;
        }
        try {
            return Math.max(0.0, Math.min(1.0, Double.parseDouble(matcher.group(1))));
        } catch (NumberFormatException ignored) {
            return 0.5;
        }
    }

    private static String parseReason(String content) {
        Matcher matcher = REASON_PATTERN.matcher(content);
        if (!matcher.find()) {
            return "AI classifier returned no reason.";
        }
        return unescapeJson(matcher.group(1));
    }

    private static String safePreview(String preview, int maxBytes) {
        if (preview == null) {
            return "";
        }
        if (preview.length() <= maxBytes) {
            return preview;
        }
        return preview.substring(0, maxBytes) + "\n...[truncated]";
    }

    private static String json(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
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
        boolean escaping = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!escaping) {
                if (c == '\\') {
                    escaping = true;
                } else {
                    builder.append(c);
                }
                continue;
            }

            switch (c) {
                case '"' -> builder.append('"');
                case '\\' -> builder.append('\\');
                case '/' -> builder.append('/');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                default -> builder.append(c);
            }
            escaping = false;
        }
        if (escaping) {
            builder.append('\\');
        }
        return builder.toString();
    }
}
