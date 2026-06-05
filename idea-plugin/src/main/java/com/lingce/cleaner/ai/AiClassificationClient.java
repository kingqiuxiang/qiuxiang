package com.lingce.cleaner.ai;

import com.lingce.cleaner.core.FileCategory;
import com.lingce.cleaner.core.FileDecision;
import com.lingce.cleaner.settings.AiFileCleanerSettings;

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

public final class AiClassificationClient {
    private static final Pattern CONTENT_PATTERN = Pattern.compile("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("\"category\"\\s*:\\s*\"([A-Z_]+)\"");
    private static final Pattern CONFIDENCE_PATTERN = Pattern.compile("\"confidence\"\\s*:\\s*([0-9.]+)");
    private static final Pattern REASON_PATTERN = Pattern.compile("\"reason\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build();

    public Optional<FileDecision> classify(String relativePath, String contentSample) {
        AiFileCleanerSettings.StateData settings = AiFileCleanerSettings.getInstance().getState();
        String apiKey = AiFileCleanerSettings.getInstance().getApiKey();
        if (settings == null || isBlank(apiKey) || isBlank(settings.baseUrl)) {
            return Optional.empty();
        }

        String requestBody = buildRequestBody(settings.model, relativePath, contentSample);
        HttpRequest request = HttpRequest.newBuilder(endpoint(settings.baseUrl))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            return parseDecision(response.body());
        } catch (IOException | InterruptedException ignored) {
            if (ignored instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    private URI endpoint(String baseUrl) {
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/chat/completions")) {
            return URI.create(normalized);
        }
        return URI.create(normalized + "/chat/completions");
    }

    private String buildRequestBody(String model, String relativePath, String contentSample) {
        String safeModel = isBlank(model) ? "gpt-4o-mini" : model.trim();
        String prompt = """
            You are an IntelliJ IDEA plugin safety classifier. Classify a newly created or scanned project file.
            Return only strict JSON with fields:
            {"category":"KEEP|TEMPORARY|AI_GENERATED_USELESS|PROJECT_CONFIG|AI_CONFIG|SUSPICIOUS","confidence":0.0-1.0,"reason":"short reason"}

            Rules:
            - TEMPORARY means cache, swap, backup, transient build/editor leftovers.
            - AI_GENERATED_USELESS means disposable AI draft/output/scratch content, not source that should be preserved.
            - PROJECT_CONFIG means local IDE/project config that should be ignored/excluded instead of deleted.
            - AI_CONFIG means AI assistant/tool config that should be ignored/excluded instead of deleted.
            - SUSPICIOUS means uncertain/unpredictable generated-looking content requiring human one-click action.
            - KEEP means normal source, docs, lockfiles, package files, or user-authored content.
            Be conservative: choose KEEP or SUSPICIOUS when unsure.

            Relative path:
            %s

            Content sample:
            %s
            """.formatted(relativePath, contentSample == null ? "" : contentSample);

        return "{"
            + "\"model\":\"" + jsonEscape(safeModel) + "\","
            + "\"temperature\":0,"
            + "\"messages\":["
            + "{\"role\":\"system\",\"content\":\"Return only strict JSON. Be conservative with deletes.\"},"
            + "{\"role\":\"user\",\"content\":\"" + jsonEscape(prompt) + "\"}"
            + "]"
            + "}";
    }

    private Optional<FileDecision> parseDecision(String responseBody) {
        Matcher contentMatcher = CONTENT_PATTERN.matcher(responseBody);
        if (!contentMatcher.find()) {
            return Optional.empty();
        }
        String content = jsonUnescape(contentMatcher.group(1)).trim();

        Matcher categoryMatcher = CATEGORY_PATTERN.matcher(content);
        Matcher confidenceMatcher = CONFIDENCE_PATTERN.matcher(content);
        Matcher reasonMatcher = REASON_PATTERN.matcher(content);
        if (!categoryMatcher.find() || !confidenceMatcher.find()) {
            return Optional.empty();
        }

        try {
            FileCategory category = FileCategory.valueOf(categoryMatcher.group(1).toUpperCase(Locale.ROOT));
            double confidence = Math.max(0.0, Math.min(1.0, Double.parseDouble(confidenceMatcher.group(1))));
            String reason = reasonMatcher.find() ? jsonUnescape(reasonMatcher.group(1)) : "AI classification";
            return Optional.of(FileDecision.of(category, confidence, reason));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String jsonEscape(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        return builder.toString();
    }

    private String jsonUnescape(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        boolean escaping = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (!escaping) {
                if (ch == '\\') {
                    escaping = true;
                } else {
                    builder.append(ch);
                }
                continue;
            }

            switch (ch) {
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case '"' -> builder.append('"');
                case '\\' -> builder.append('\\');
                default -> builder.append(ch);
            }
            escaping = false;
        }
        return builder.toString();
    }
}
