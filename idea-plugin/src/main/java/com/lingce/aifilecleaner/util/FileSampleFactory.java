package com.lingce.aifilecleaner.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.lingce.aifilecleaner.classifier.FileSample;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileSampleFactory {
    private FileSampleFactory() {
    }

    public static @Nullable FileSample fromVirtualFile(Project project, VirtualFile file, int maxSampleBytes) {
        if (!file.isValid() || !file.isInLocalFileSystem()) {
            return null;
        }

        VirtualFile base = project.getBaseDir();
        String relativePath = base == null ? file.getPath() : VfsUtilCore.getRelativePath(file, base, '/');
        if (relativePath == null || relativePath.isBlank()) {
            relativePath = file.getName();
        }

        if (file.isDirectory()) {
            return new FileSample(
                    file.getPath(),
                    relativePath,
                    file.getName(),
                    true,
                    0,
                    "",
                    false
            );
        }

        Path nioPath = VfsUtilCore.virtualToIoFile(file).toPath();
        try {
            long size = Files.size(nioPath);
            int sampleSize = (int) Math.min(Math.max(1024, maxSampleBytes), Math.min(size, Integer.MAX_VALUE));
            byte[] bytes = readPrefix(nioPath, sampleSize);
            boolean binary = containsBinaryByte(bytes);
            String snippet = binary ? "" : new String(bytes, StandardCharsets.UTF_8);
            return new FileSample(file.getPath(), relativePath, file.getName(), false, size, snippet, binary);
        } catch (IOException error) {
            return null;
        }
    }

    private static byte[] readPrefix(Path path, int maxBytes) throws IOException {
        try (InputStream stream = Files.newInputStream(path)) {
            return stream.readNBytes(maxBytes);
        }
    }

    private static boolean containsBinaryByte(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) {
                return true;
            }
        }
        return false;
    }
}
