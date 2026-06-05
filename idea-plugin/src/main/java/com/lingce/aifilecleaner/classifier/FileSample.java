package com.lingce.aifilecleaner.classifier;

public final class FileSample {
    private final String absolutePath;
    private final String relativePath;
    private final String fileName;
    private final boolean directory;
    private final long sizeBytes;
    private final String contentSnippet;
    private final boolean binary;

    public FileSample(
            String absolutePath,
            String relativePath,
            String fileName,
            boolean directory,
            long sizeBytes,
            String contentSnippet,
            boolean binary
    ) {
        this.absolutePath = absolutePath;
        this.relativePath = relativePath;
        this.fileName = fileName;
        this.directory = directory;
        this.sizeBytes = sizeBytes;
        this.contentSnippet = contentSnippet;
        this.binary = binary;
    }

    public String absolutePath() {
        return absolutePath;
    }

    public String relativePath() {
        return relativePath;
    }

    public String fileName() {
        return fileName;
    }

    public boolean directory() {
        return directory;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public String contentSnippet() {
        return contentSnippet;
    }

    public boolean binary() {
        return binary;
    }

    public String normalizedRelativePath() {
        return relativePath.replace('\\', '/').toLowerCase();
    }
}
