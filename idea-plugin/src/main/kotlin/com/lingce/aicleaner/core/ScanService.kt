package com.lingce.aicleaner.core

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.lingce.aicleaner.model.ClassificationResult
import com.lingce.aicleaner.model.FileCategory
import com.lingce.aicleaner.settings.AiCleanerSettings

/**
 * 遍历项目并对文件进行分类，返回需要处理（非 NORMAL）的结果。
 */
class ScanService(private val project: Project) {

    private val classifier = FileClassifier(project)

    fun scan(roots: List<VirtualFile>, indicator: ProgressIndicator?): List<ClassificationResult> {
        val settings = AiCleanerSettings.getInstance()
        val allowAi = settings.isAiConfigured
        val results = ArrayList<ClassificationResult>()

        for (root in roots) {
            ReadAction.run<RuntimeException> {
                VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Any>() {
                    override fun visitFileEx(file: VirtualFile): Result {
                        indicator?.checkCanceled()

                        // 跳过受保护目录的整棵子树
                        if (file.isDirectory && shouldSkipDir(file)) {
                            return SKIP_CHILDREN
                        }

                        indicator?.text2 = relPath(file)

                        val result = classifier.classify(file, allowAi = allowAi)

                        // 命中需要整体处理的目录（AI/项目配置目录）：记录并跳过子树
                        if (file.isDirectory) {
                            if (result.category == FileCategory.AI_CONFIG ||
                                result.category == FileCategory.PROJECT_CONFIG
                            ) {
                                results.add(result)
                                return SKIP_CHILDREN
                            }
                            return CONTINUE
                        }

                        if (result.actionable) results.add(result)
                        return CONTINUE
                    }
                })
            }
        }
        return results
    }

    private fun shouldSkipDir(dir: VirtualFile): Boolean {
        val name = dir.name
        return name in SKIP_DIRS
    }

    private fun relPath(file: VirtualFile): String {
        val base = ProjectPaths.baseDir(project) ?: return file.name
        return VfsUtilCore.getRelativePath(file, base) ?: file.name
    }

    companion object {
        private val SKIP_DIRS = setOf(
            ".git", "node_modules", ".gradle", "build", "dist", "out", "target",
            ".idea", "venv", ".venv", "__pycache__", ".mvn",
        )
    }
}
