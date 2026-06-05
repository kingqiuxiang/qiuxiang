package com.lingce.cleanguard.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.lingce.cleanguard.detection.AiDetectionService
import com.lingce.cleanguard.detection.FileClassifier
import com.lingce.cleanguard.model.ClassifiedFile
import com.lingce.cleanguard.model.FileCategory
import com.lingce.cleanguard.settings.CleanGuardSettings
import java.util.concurrent.CopyOnWriteArrayList

class ScanService(private val project: Project) {

    private val aiService = AiDetectionService()
    val results = CopyOnWriteArrayList<ClassifiedFile>()

    fun scanProject(onComplete: (List<ClassifiedFile>) -> Unit) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Clean Guard 扫描项目", true) {
            override fun run(indicator: ProgressIndicator) {
                val found = mutableListOf<ClassifiedFile>()
                val root = project.basePath?.let {
                    com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(it)
                } ?: return

                indicator.isIndeterminate = false
                val files = collectFiles(root)
                files.forEachIndexed { index, pair ->
                    indicator.fraction = (index + 1).toDouble() / files.size.coerceAtLeast(1)
                    indicator.text = "扫描: ${pair.second}"
                    if (indicator.isCanceled) return@forEachIndexed

                    val classified = classify(pair.first, pair.second)
                    if (classified.category != FileCategory.SAFE && classified.category != FileCategory.UNKNOWN) {
                        found.add(classified)
                    } else if (classified.category == FileCategory.UNKNOWN) {
                        val enhanced = enhanceWithAi(pair.first, pair.second, classified)
                        if (enhanced.category != FileCategory.SAFE) {
                            found.add(enhanced)
                        }
                    }
                }

                results.clear()
                results.addAll(found)
                ApplicationManager.getApplication().invokeLater {
                    onComplete(found)
                }
            }
        })
    }

    fun classifyFile(file: VirtualFile): ClassifiedFile {
        val relative = relativePath(file) ?: file.path
        return classify(file, relative)
    }

    private fun classify(file: VirtualFile, relativePath: String): ClassifiedFile {
        val base = FileClassifier.classify(file, relativePath)
        if (base.category == FileCategory.UNKNOWN) {
            return enhanceWithAi(file, relativePath, base)
        }
        return base
    }

    private fun enhanceWithAi(file: VirtualFile, relativePath: String, base: ClassifiedFile): ClassifiedFile {
        val settings = CleanGuardSettings.getInstance().state
        if (!settings.useAiDetection || settings.apiKey.isBlank() || file.isDirectory) {
            return base
        }
        val content = FileClassifier.readContentHint(file) ?: return base
        return aiService.classifyWithAi(relativePath, content) ?: base
    }

    private fun collectFiles(root: VirtualFile): List<Pair<VirtualFile, String>> {
        val list = mutableListOf<Pair<VirtualFile, String>>()
        VfsUtil.iterateChildrenRecursively(root, null) { file ->
            if (FileClassifier.isUnderIgnoredVcs(file)) {
                return@iterateChildrenRecursively false
            }
            val relative = relativePath(file)
            if (relative != null) {
                list.add(file to relative)
            }
            true
        }
        return list
    }

    private fun relativePath(file: VirtualFile): String? {
        val base = project.basePath ?: return null
        val baseFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(base)
            ?: return null
        return VfsUtil.getRelativePath(file, baseFile)?.replace('\\', '/')
    }

    companion object {
        fun forProject(project: Project): ScanService = ScanService(project)
    }
}
