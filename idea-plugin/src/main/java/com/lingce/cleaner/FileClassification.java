package com.lingce.cleaner;

final class FileClassification {
    enum Category {
        SAFE,
        TEMPORARY,
        CONFIGURATION,
        DISPOSABLE_AI,
        SUSPICIOUS_AI,
        UNKNOWN
    }

    private final Category category;
    private final String reason;

    FileClassification(Category category, String reason) {
        this.category = category;
        this.reason = reason;
    }

    Category category() {
        return category;
    }

    String reason() {
        return reason;
    }
}
