package com.lingce.aifilecleaner.model

import java.nio.file.Path

data class ScanResult(
    val scannedFiles: Int = 0,
    val deletedTmpFiles: Int = 0,
    val deletedAiJunkFiles: Int = 0,
    val movedConfigFiles: Int = 0,
    val suspiciousFiles: List<Path> = emptyList(),
    val errors: List<String> = emptyList()
)
