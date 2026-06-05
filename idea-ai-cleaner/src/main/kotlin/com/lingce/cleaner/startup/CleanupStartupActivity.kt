package com.lingce.cleaner.startup

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.lingce.cleaner.services.FileCleanupService

class CleanupStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<FileCleanupService>().initializeAndScan()
    }
}
