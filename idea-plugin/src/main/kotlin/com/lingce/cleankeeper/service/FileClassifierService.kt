package com.lingce.cleankeeper.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.lingce.cleankeeper.model.FileClassification
import com.lingce.cleankeeper.settings.CleanKeeperSettings

@Service(Service.Level.PROJECT)
class FileClassifierService(private val project: Project) {

    private val aiClient = AiClassifierClient()

    fun classify(file: VirtualFile): FileClassification {
        val relativePath = getRelativePath(file)
        val content = HeuristicClassifier.readContent(file)

        val heuristic = HeuristicClassifier.classify(file, relativePath, content)
        if (heuristic != null && heuristic.confidence >= 0.85) {
            return heuristic
        }

        val settings = CleanKeeperSettings.getInstance()
        if (settings.isAiConfigured() && settings.useAiClassification) {
            val aiResult = aiClient.classify(file.name, relativePath, content)
            if (aiResult != null && aiResult.confidence >= settings.aiConfidenceThreshold) {
                return aiResult
            }
            if (heuristic != null && aiResult != null) {
                return if (aiResult.confidence > heuristic.confidence) aiResult else heuristic
            }
            if (aiResult != null) return aiResult
        }

        return heuristic ?: FileClassification(
            com.lingce.cleankeeper.model.FileCategory.SAFE,
            0.5,
            "未匹配任何规则，视为安全文件",
            com.lingce.cleankeeper.model.ClassificationSource.HEURISTIC,
        )
    }

    fun getRelativePath(file: VirtualFile): String {
        val base = project.basePath ?: return file.path
        val path = file.path.replace('\\', '/')
        val baseNorm = base.replace('\\', '/')
        return if (path.startsWith(baseNorm)) {
            path.removePrefix(baseNorm).removePrefix("/")
        } else {
            file.name
        }
    }

    companion object {
        fun getInstance(project: Project): FileClassifierService =
            project.getService(FileClassifierService::class.java)
    }
}
