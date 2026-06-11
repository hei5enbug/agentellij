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
        ClaudeCodeProfile(),
        CodexCliProfile()
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
        return resolveProfile(
            activeAgentId = activeAgentId,
            settingsPath = settings.getAgentPath(activeAgentId),
            agentellijBin = System.getenv("AGENTELLIJ_BIN"),
            profiles = profiles
        )
    }

    fun allProfiles(): List<AgentProfile> = profiles.toList()

    internal fun resolveProfile(
        activeAgentId: String,
        settingsPath: String,
        agentellijBin: String?,
        profiles: List<AgentProfile>
    ): AgentProfile {
        profiles.find { it.id == activeAgentId }?.let { return it }

        settingsPath.trim().takeIf { it.isNotEmpty() }?.let { path ->
            matchByBinaryName(path, profiles)?.let { return it }
        }

        agentellijBin?.trim()?.takeIf { it.isNotEmpty() }?.let { path ->
            matchByBinaryName(path, profiles)?.let { return it }
        }

        return profiles.first()
    }

    private fun matchByBinaryName(path: String, profiles: List<AgentProfile>): AgentProfile? {
        val name = File(path).nameWithoutExtension.lowercase()
        return profiles.find { it.defaultBinary == name }
    }
}
