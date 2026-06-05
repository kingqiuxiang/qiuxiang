package com.lingce.cleaner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

final class AiRemoteClassifier {
    private static final int MAX_REMOTE_CHARS = 12_000;

    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build();

    Optional<RemoteClassification> classify(String relativePath, String content, AiCleanerSettings.StateData settings) {
        if (!settings.remoteDetectionEnabled
            || AiCleanerSettings.isBlank(settings.baseUrl)
            || AiCleanerSettings.isBlank(settings.apiKey)) {
            return Optional.empty();
        }

        String excerpt = content.length() > MAX_REMOTE_CHARS ? content.substring(0, MAX_REMOTE_CHARS) : content;
        String prompt = """
            You classify files for an IDE cleanup plugin.
            Return compact JSON only, without markdown:
            {"aiGenerated":true|false,"disposable":true|false,"reason":"short reason"}

            Definitions:
            - aiGenerated=true if the file appears produced by ChatGPT, Claude, Cursor, Copilot, an LLM agent, or an AI code assistant.
            - disposable=true only if it is a draft, scratch, temporary output, generated note, prompt result, or otherwise safe to remove from a source tree.
            - Never mark normal project source, user documentation, lock files, or build configuration as disposable.

            Path: %s
            Content:
            %s
            """.formatted(relativePath, excerpt);

        String endpoint = normalizeEndpoint(settings.baseUrl);
        String requestJson = """
            {"model":%s,"messages":[{"role":"system","content":"You are a conservative file cleanup classifier."},{"role":"user","content":%s}],"temperature":0}
            """.formatted(jsonString(settings.model), jsonString(prompt));

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + settings.apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(requestJson))
            .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            return parseOpenAiResponse(response.body());
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    private Optional<RemoteClassification> parseOpenAiResponse(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            return Optional.empty();
        }

        JsonObject firstChoice = choices.get(0).getAsJsonObject();
        JsonObject message = firstChoice.getAsJsonObject("message");
        if (message == null) {
            return Optional.empty();
        }

        JsonElement contentElement = message.get("content");
        if (contentElement == null) {
            return Optional.empty();
        }

        String content = stripCodeFence(contentElement.getAsString().trim());
        JsonObject result = JsonParser.parseString(content).getAsJsonObject();
        boolean aiGenerated = getBoolean(result, "aiGenerated");
        boolean disposable = getBoolean(result, "disposable");
        String reason = result.has("reason") ? result.get("reason").getAsString() : "Remote classifier";
        return Optional.of(new RemoteClassification(aiGenerated, disposable, reason));
    }

    private boolean getBoolean(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() && value.getAsBoolean();
    }

    private String normalizeEndpoint(String baseUrl) {
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/chat/completions")) {
            return trimmed;
        }
        if (trimmed.endsWith("/")) {
            return trimmed + "chat/completions";
        }
        return trimmed + "/chat/completions";
    }

    private String stripCodeFence(String text) {
        if (!text.startsWith("```")) {
            return text;
        }

        int firstNewline = text.indexOf('\n');
        int lastFence = text.lastIndexOf("```");
        if (firstNewline >= 0 && lastFence > firstNewline) {
            return text.substring(firstNewline + 1, lastFence).trim();
        }
        return text;
    }

    private String jsonString(String value) {
        String safe = value == null ? "" : value;
        StringBuilder builder = new StringBuilder(safe.length() + 2);
        builder.append('"');
        for (int i = 0; i < safe.length(); i++) {
            char ch = safe.charAt(i);
            switch (ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
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
        builder.append('"');
        return builder.toString();
    }
}
