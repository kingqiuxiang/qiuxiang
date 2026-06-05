package com.lingce.cleanguard

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.lingce.cleanguard.service.ImportWatchService
import com.lingce.cleanguard.settings.CleanGuardSettings

class CleanGuardStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        if (CleanGuardSettings.getInstance().state.enabled) {
            ImportWatchService.getOrStart(project)
        }
    }
}
