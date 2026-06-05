package com.lingce.aicleaner;

final class ClassificationResult {
    private final FileCategory category;
    private final double confidence;
    private final String reason;

    ClassificationResult(FileCategory category, double confidence, String reason) {
        this.category = category;
        this.confidence = confidence;
        this.reason = reason == null ? "" : reason;
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

    boolean isActionable() {
        return category != FileCategory.NORMAL && category != FileCategory.UNKNOWN;
    }

    @Override
    public String toString() {
        return category.getLabel() + " (" + Math.round(confidence * 100) + "%): " + reason;
    }
}
