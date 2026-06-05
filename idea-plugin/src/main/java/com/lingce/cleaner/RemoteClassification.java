package com.lingce.cleaner;

final class RemoteClassification {
    private final boolean aiGenerated;
    private final boolean disposable;
    private final String reason;

    RemoteClassification(boolean aiGenerated, boolean disposable, String reason) {
        this.aiGenerated = aiGenerated;
        this.disposable = disposable;
        this.reason = reason;
    }

    boolean aiGenerated() {
        return aiGenerated;
    }

    boolean disposable() {
        return disposable;
    }

    String reason() {
        return reason;
    }
}
