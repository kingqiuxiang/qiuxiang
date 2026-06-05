package com.lingce.cleanguardian

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class CleanGuardianStartupActivity : StartupActivity.DumbAware {
    override fun runActivity(project: Project) {
        project.getService(CleanGuardianProjectService::class.java).onProjectOpened()
    }
}
