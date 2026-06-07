package com.agentellij.settings

import com.agentellij.backend.AgentProfile

/**
 * Centralizes mode validation so Settings, toolbar actions, and tool-window startup
 * all enforce the same agent capability rules.
 */
object AgentModePolicy {
    const val DEFAULT_AGENT_ID = "opencode"
    const val DEFAULT_MODE = "tui"

    fun normalizeMode(mode: String?): String =
        when (mode?.lowercase()) {
            "gui", "tui" -> mode.lowercase()
            else -> DEFAULT_MODE
        }

    fun normalizeModeForProfile(mode: String?, profile: AgentProfile): String =
        normalizeModeForSupportedModes(mode, profile.supportedModes)

    fun normalizeModeForSupportedModes(mode: String?, supportedModes: List<String>): String {
        val normalized = normalizeMode(mode)
        val normalizedSupportedModes = supportedModes
            .map { normalizeMode(it) }
            .distinct()

        return when {
            normalized in normalizedSupportedModes -> normalized
            DEFAULT_MODE in normalizedSupportedModes -> DEFAULT_MODE
            else -> normalizedSupportedModes.firstOrNull() ?: DEFAULT_MODE
        }
    }

    fun resolveProfile(agentId: String?, profiles: List<AgentProfile>): AgentProfile {
        val normalizedAgentId = agentId?.takeIf { it.isNotBlank() } ?: DEFAULT_AGENT_ID
        return profiles.find { it.id == normalizedAgentId }
            ?: profiles.find { it.id == DEFAULT_AGENT_ID }
            ?: profiles.first()
    }
}
