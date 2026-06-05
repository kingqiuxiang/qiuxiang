package com.aifileguard.action

import com.aifileguard.model.FileVerdict
import com.aifileguard.model.SuggestedAction
import com.aifileguard.settings.AiGuardSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Executes the recommended (or user chosen) action for files.
 */
class FileActionExecutor(private val project: Project) {

    data class Outcome(
        var deleted: Int = 0,
        var ignored: Int = 0,
        var quarantined: Int = 0,
        var kept: Int = 0,
        var failed: Int = 0,
        val errors: MutableList<String> = mutableListOf(),
    )

    /** Apply each verdict's own [SuggestedAction]. */
    fun applyRecommended(verdicts: List<FileVerdict>): Outcome =
        apply(verdicts.map { it to it.action })

    /** Apply a single explicit action to every given verdict. */
    fun applyAction(verdicts: List<FileVerdict>, action: SuggestedAction): Outcome =
        apply(verdicts.map { it to action })

    private fun apply(items: List<Pair<FileVerdict, SuggestedAction>>): Outcome {
        val outcome = Outcome()
        for ((verdict, action) in items) {
            try {
                when (action) {
                    SuggestedAction.DELETE -> { if (delete(verdict)) outcome.deleted++ else outcome.failed++ }
                    SuggestedAction.QUARANTINE -> { if (quarantine(verdict)) outcome.quarantined++ else outcome.failed++ }
                    SuggestedAction.ADD_TO_IGNORE -> { if (addToIgnore(verdict)) outcome.ignored++ else outcome.failed++ }
                    SuggestedAction.KEEP -> outcome.kept++
                }
            } catch (t: Throwable) {
                LOG.warn("Action $action failed for ${verdict.relativePath}", t)
                outcome.failed++
                outcome.errors.add("${verdict.relativePath}: ${t.message}")
            }
        }
        return outcome
    }

    private fun delete(verdict: FileVerdict): Boolean {
        val vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(verdict.absolutePath) ?: return false
        WriteCommandAction.runWriteCommandAction(project) {
            runCatching { vFile.delete(this) }
        }
        return !vFile.isValid || !Files.exists(Path.of(verdict.absolutePath))
    }

    private fun quarantine(verdict: FileVerdict): Boolean {
        val targetRoot = AiGuardSettings.getInstance().state.quarantineDir
        if (targetRoot.isBlank()) {
            throw IllegalStateException("Quarantine directory is not configured (Settings → Tools → AI File Guard)")
        }
        val source = Path.of(verdict.absolutePath)
        if (!Files.exists(source)) return false
        val target = Path.of(targetRoot).resolve(verdict.relativePath)
        Files.createDirectories(target.parent)
        val finalTarget = uniqueTarget(target)
        WriteCommandAction.runWriteCommandAction(project) {
            runCatching {
                Files.move(source, finalTarget, StandardCopyOption.REPLACE_EXISTING)
            }.onFailure {
                // Cross-device fallback: copy then delete.
                Files.copy(source, finalTarget, StandardCopyOption.REPLACE_EXISTING)
                Files.deleteIfExists(source)
            }
        }
        LocalFileSystem.getInstance().refreshAndFindFileByPath(verdict.absolutePath)
        VfsUtil.markDirtyAndRefresh(true, true, true,
            *listOfNotNull(LocalFileSystem.getInstance().findFileByNioFile(source.parent)).toTypedArray())
        return !Files.exists(source)
    }

    private fun uniqueTarget(target: Path): Path {
        if (!Files.exists(target)) return target
        val name = target.fileName.toString()
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        var candidate = target.parent.resolve("$base.$i$ext")
        while (Files.exists(candidate)) {
            i++
            candidate = target.parent.resolve("$base.$i$ext")
        }
        return candidate
    }

    private fun addToIgnore(verdict: FileVerdict): Boolean {
        val root = project.guessProjectDir()?.let { runCatching { it.toNioPath() }.getOrNull() } ?: return false
        val entry = verdict.relativePath
        var changed = false
        changed = appendUnique(root.resolve(".gitignore"), entry) || changed
        val gitInfo = root.resolve(".git").resolve("info")
        if (Files.isDirectory(root.resolve(".git"))) {
            runCatching { Files.createDirectories(gitInfo) }
            changed = appendUnique(gitInfo.resolve("exclude"), entry) || changed
        }
        ApplicationManager.getApplication().invokeLater {
            LocalFileSystem.getInstance().refreshAndFindFileByPath(root.resolve(".gitignore").toString())
        }
        return changed
    }

    private fun appendUnique(file: Path, entry: String): Boolean {
        val existing = if (Files.exists(file)) Files.readAllLines(file).map { it.trim() } else emptyList()
        if (existing.contains(entry.trim())) return false
        val prefix = if (Files.exists(file) && Files.size(file) > 0) "\n" else ""
        Files.writeString(
            file,
            "$prefix# Added by AI File Guard\n$entry\n",
            Charsets.UTF_8,
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND,
        )
        return true
    }

    companion object {
        private val LOG = logger<FileActionExecutor>()
    }
}
