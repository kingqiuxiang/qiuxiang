package com.lingce.aicleaner;

import java.time.Instant;

final class SuspectFileRecord {
    private final String path;
    private final FileCategory category;
    private final double confidence;
    private final String reason;
    private final Instant detectedAt;

    SuspectFileRecord(String path, ClassificationResult result) {
        this.path = path;
        this.category = result.getCategory();
        this.confidence = result.getConfidence();
        this.reason = result.getReason();
        this.detectedAt = Instant.now();
    }

    String getPath() {
        return path;
    }

    FileCategory getCategory() {
        return category;
    }

    double getConfidence() {
        return confidence;
    }

    String getReason() {
        return reason;
    }

    Instant getDetectedAt() {
        return detectedAt;
    }

    @Override
    public String toString() {
        return path + " - " + category.getLabel() + " (" + Math.round(confidence * 100) + "%)";
    }
}
