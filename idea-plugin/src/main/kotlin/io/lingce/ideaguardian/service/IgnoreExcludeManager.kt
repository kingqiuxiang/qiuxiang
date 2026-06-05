package io.lingce.ideaguardian.service

import com.intellij.openapi.project.Project
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class IgnoreExcludeManager {
    fun append(project: Project, relativePaths: List<String>) {
        if (relativePaths.isEmpty()) return
        val base = project.basePath?.let(Path::of) ?: return

        val ignoreExcludeDir = base.resolve("ignore").resolve("exclude")
        Files.createDirectories(ignoreExcludeDir)
        val managedList = ignoreExcludeDir.resolve("managed-ignore-list.txt")
        appendUniqueLines(managedList, relativePaths)

        val gitExclude = base.resolve(".git").resolve("info").resolve("exclude")
        if (Files.exists(gitExclude)) {
            appendUniqueLines(gitExclude, relativePaths)
        }
    }

    private fun appendUniqueLines(target: Path, lines: List<String>) {
        val existing = if (Files.exists(target)) {
            Files.readAllLines(target, StandardCharsets.UTF_8).toMutableSet()
        } else {
            mutableSetOf()
        }
        val toAppend = lines.filter { it.isNotBlank() && !existing.contains(it) }
        if (toAppend.isEmpty()) return
        Files.createDirectories(target.parent)
        Files.write(
            target,
            ("\n# Added by AI File Guardian\n" + toAppend.joinToString("\n") + "\n").toByteArray(StandardCharsets.UTF_8),
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND
        )
    }
}
