package com.agentellij.core.settings

import com.agentellij.core.agent.AgentProfile
import java.io.File

/**
 * Resolves an [AgentProfile] from explicit settings and environment inputs.
 *
 * Resolution order:
 * 1. Match the active agent ID against known profiles
 * 2. Match the settings binary path filename against known profiles (legacy)
 * 3. Match the `AGENTELLIJ_BIN` env var filename against known profiles
 * 4. Fall back to the first profile
 */
object ActiveAgentSelector {
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
