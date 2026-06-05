package com.lingce.aifilecleaner.classifier;

public interface FileClassifier {
    FileClassification classify(FileSample sample);
}
