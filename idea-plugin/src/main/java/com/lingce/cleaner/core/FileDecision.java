package com.lingce.cleaner.core;

public final class FileDecision {
    private final FileCategory category;
    private final double confidence;
    private final String reason;

    private FileDecision(FileCategory category, double confidence, String reason) {
        this.category = category;
        this.confidence = confidence;
        this.reason = reason == null ? "" : reason;
    }

    public static FileDecision keep(String reason) {
        return new FileDecision(FileCategory.KEEP, 1.0, reason);
    }

    public static FileDecision of(FileCategory category, double confidence, String reason) {
        return new FileDecision(category, confidence, reason);
    }

    public FileCategory getCategory() {
        return category;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getReason() {
        return reason;
    }

    public boolean isActionable() {
        return category != FileCategory.KEEP;
    }
}
