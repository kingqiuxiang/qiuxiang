package com.aifileguard.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/**
 * Application level, persisted settings for AI File Guard.
 */
@Service(Service.Level.APP)
@State(name = "AiFileGuardSettings", storages = [Storage("aiFileGuard.xml")])
class AiGuardSettings : PersistentStateComponent<AiGuardSettings.State> {

    data class State(
        // ---- AI / LLM connection ----
        var baseUrl: String = "https://api.openai.com/v1",
        var apiKey: String = "",
        var model: String = "gpt-4o-mini",
        var enableAi: Boolean = true,

        // ---- behaviour ----
        var scanOnOpen: Boolean = true,
        var watchNewFiles: Boolean = true,
        /** Automatically apply DELETE/ADD_TO_IGNORE for high confidence rule based verdicts. */
        var autoApplySafe: Boolean = false,
        /** Confidence threshold (0..1) above which auto apply is allowed. */
        var autoApplyThreshold: Double = 0.9,

        // ---- quarantine ----
        var quarantineDir: String = "",

        // ---- classification rules (newline / comma separated glob-ish patterns) ----
        var tmpPatterns: String = DEFAULT_TMP_PATTERNS,
        var aiArtifactPatterns: String = DEFAULT_AI_ARTIFACT_PATTERNS,
        var projectConfigPatterns: String = DEFAULT_PROJECT_CONFIG_PATTERNS,
        var aiConfigPatterns: String = DEFAULT_AI_CONFIG_PATTERNS,

        /** Max file size (KB) whose text content is sent to the AI for classification. */
        var maxAiFileSizeKb: Int = 256,
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(): AiGuardSettings = service()

        // Default patterns. Lines starting with '#' are treated as comments.
        val DEFAULT_TMP_PATTERNS = """
            *.tmp
            *.temp
            *.bak
            *.swp
            *.swo
            *~
            *.orig
            *.cache
            *.log
            .DS_Store
            Thumbs.db
            *.pyc
            *.class
            *.o
            *.obj
        """.trimIndent()

        val DEFAULT_AI_ARTIFACT_PATTERNS = """
            *_ai_generated*
            *-ai-generated*
            *.ai.tmp
            ai-scratch*
            ai_output*
            ai-notes*
            chatgpt*.md
            *_copilot_*
            *.codegen.tmp
        """.trimIndent()

        val DEFAULT_PROJECT_CONFIG_PATTERNS = """
            .env
            .env.*
            *.local
            local.properties
            *.iml
        """.trimIndent()

        val DEFAULT_AI_CONFIG_PATTERNS = """
            .cursor/**
            .cursorrules
            .cursorignore
            .aider*
            .continue/**
            .github/copilot-instructions.md
            .codeium/**
            .windsurfrules
            CLAUDE.md
            AGENTS.md
            .claude/**
        """.trimIndent()
    }
}
