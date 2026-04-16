package com.agentellij.backend

import com.agentellij.settings.AgentellIJSettings
import java.io.File

/**
 * Resolves the active [AgentProfile] based on the configured binary name.
 *
 * Resolution order:
 * 1. Match Settings binary path filename against known profiles
 * 2. Match `AGENTELLIJ_BIN` env var filename against known profiles
 * 3. Fall back to [OpenCodeProfile]
 */
object AgentProfileResolver {
    private val profiles: List<AgentProfile> = listOf(
        OpenCodeProfile(),
        ClaudeCodeProfile()
    )

    /**
     * Resolve the agent profile from current settings and environment.
     *
     * Resolution order:
     * 1. Match [AgentellIJSettings.State.activeAgent] against known profile IDs
     * 2. Match Settings binary path filename against known profiles (legacy)
     * 3. Match `AGENTELLIJ_BIN` env var filename against known profiles
     * 4. Fall back to [OpenCodeProfile]
     */
    fun resolve(): AgentProfile {
        val settings = AgentellIJSettings.getInstance()

        val activeAgentId = settings.getActiveAgent()
        profiles.find { it.id == activeAgentId }?.let { return it }

        val settingsPath = settings.getAgentPath(activeAgentId).trim()
        if (settingsPath.isNotEmpty()) {
            matchByBinaryName(settingsPath)?.let { return it }
        }

        val agentEnv = System.getenv("AGENTELLIJ_BIN")?.trim()
        if (!agentEnv.isNullOrEmpty()) {
            matchByBinaryName(agentEnv)?.let { return it }
        }

        return profiles.first()
    }

    fun allProfiles(): List<AgentProfile> = profiles.toList()

    private fun matchByBinaryName(path: String): AgentProfile? {
        val name = File(path).nameWithoutExtension.lowercase()
        return profiles.find { it.defaultBinary == name }
    }
}
