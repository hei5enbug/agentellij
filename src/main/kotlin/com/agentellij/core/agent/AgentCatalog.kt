package com.agentellij.core.agent

/**
 * Provides the supported agent profile records.
 */
object AgentCatalog {
    private val profiles: List<AgentProfile> = listOf(
        OpenCodeProfile(),
        ClaudeCodeProfile(),
        CodexCliProfile(),
        TerminalProfile()
    )

    fun allProfiles(): List<AgentProfile> = profiles.toList()
}
