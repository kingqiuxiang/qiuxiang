package com.lingce.aicleaner;

enum FileCategory {
    TEMP("Temporary file"),
    AI_GENERATED_USELESS("AI-generated throwaway file"),
    PROJECT_CONFIG("Project configuration file"),
    AI_CONFIG("AI assistant configuration file"),
    SUSPICIOUS("Suspicious or unknown file"),
    NORMAL("Normal project file"),
    UNKNOWN("Unknown");

    private final String label;

    FileCategory(String label) {
        this.label = label;
    }

    String getLabel() {
        return label;
    }
}
