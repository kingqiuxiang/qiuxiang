package com.lingce.cleaner.core;

import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class FileContentSampler {
    public String sample(VirtualFile file, int maxBytes) {
        if (file.isDirectory() || !file.isInLocalFileSystem()) {
            return "";
        }
        int limit = Math.max(1024, maxBytes);
        try (InputStream input = file.getInputStream()) {
            byte[] buffer = input.readNBytes(limit);
            if (looksBinary(buffer)) {
                return "";
            }
            return new String(buffer, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "";
        }
    }

    private boolean looksBinary(byte[] bytes) {
        int inspected = Math.min(bytes.length, 2048);
        for (int i = 0; i < inspected; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }
}
