package com.lingce.aifilecleaner.classifier;

public final class FileClassification {
    public enum Kind {
        SAFE,
        TEMPORARY,
        AI_GENERATED,
        PROJECT_CONFIG,
        AI_CONFIG,
        SUSPICIOUS
    }

    public enum RecommendedAction {
        NONE,
        DELETE,
        IGNORE_EXCLUDE,
        ASK
    }

    private final Kind kind;
    private final RecommendedAction recommendedAction;
    private final double confidence;
    private final String reason;
    private final String source;

    public FileClassification(
            Kind kind,
            RecommendedAction recommendedAction,
            double confidence,
            String reason,
            String source
    ) {
        this.kind = kind;
        this.recommendedAction = recommendedAction;
        this.confidence = confidence;
        this.reason = reason;
        this.source = source;
    }

    public static FileClassification safe(String reason) {
        return new FileClassification(Kind.SAFE, RecommendedAction.NONE, 1.0, reason, "heuristic");
    }

    public Kind kind() {
        return kind;
    }

    public RecommendedAction recommendedAction() {
        return recommendedAction;
    }

    public double confidence() {
        return confidence;
    }

    public String reason() {
        return reason;
    }

    public String source() {
        return source;
    }

    public boolean requiresUserDecision() {
        return recommendedAction == RecommendedAction.ASK || kind == Kind.SUSPICIOUS;
    }
}
