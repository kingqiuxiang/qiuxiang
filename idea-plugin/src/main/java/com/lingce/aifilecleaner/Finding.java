package com.lingce.aifilecleaner;

import com.intellij.openapi.vfs.VirtualFile;
import com.lingce.aifilecleaner.classifier.FileClassification;

import java.time.Instant;

public final class Finding {
    private final String path;
    private final VirtualFile virtualFile;
    private final FileClassification classification;
    private final Instant detectedAt;

    public Finding(String path, VirtualFile virtualFile, FileClassification classification, Instant detectedAt) {
        this.path = path;
        this.virtualFile = virtualFile;
        this.classification = classification;
        this.detectedAt = detectedAt;
    }

    public String path() {
        return path;
    }

    public VirtualFile virtualFile() {
        return virtualFile;
    }

    public FileClassification classification() {
        return classification;
    }

    public Instant detectedAt() {
        return detectedAt;
    }

    @Override
    public String toString() {
        return classification.kind() + " · " + path + " · " + classification.reason();
    }
}
