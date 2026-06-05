package com.lingce.aicleaner.core

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

/** 获取项目根目录的工具，避免依赖仅在部分模块可用的 ProjectUtil。 */
object ProjectPaths {
    fun baseDir(project: Project): VirtualFile? {
        val path = project.basePath ?: return null
        return LocalFileSystem.getInstance().findFileByPath(path)
    }
}
