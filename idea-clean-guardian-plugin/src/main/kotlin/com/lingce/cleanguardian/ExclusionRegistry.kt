package com.lingce.cleanguardian

import com.intellij.openapi.project.Project
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class ExclusionRegistry(private val project: Project) {
    private val registryFile: Path?
        get() {
            val basePath = project.basePath ?: return null
            return Path.of(basePath, ".clean-guardian", "ignore", "exclude", "paths.txt")
        }

    fun isExcluded(relativePath: String): Boolean {
        val normalized = normalize(relativePath)
        return loadAllPaths().contains(normalized)
    }

    fun addPath(relativePath: String) {
        addPaths(listOf(relativePath))
    }

    fun addPaths(relativePaths: Collection<String>) {
        if (relativePaths.isEmpty()) return
        val file = registryFile ?: return
        val normalizedIncoming = relativePaths.map { normalize(it) }.toSet()
        val existing = loadAllPaths()
        val merged = (existing + normalizedIncoming).sorted()

        Files.createDirectories(file.parent)
        Files.write(
            file,
            merged.joinToString("\n", postfix = "\n").toByteArray(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
    }

    private fun loadAllPaths(): Set<String> {
        val file = registryFile ?: return emptySet()
        if (!Files.exists(file)) return emptySet()
        return Files.readAllLines(file, StandardCharsets.UTF_8)
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { normalize(it) }
            .toSet()
    }

    private fun normalize(path: String): String {
        return path.replace('\\', '/').trim().removePrefix("./")
    }
}
