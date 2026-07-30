package com.agentellij.core.launch

import com.agentellij.core.agent.AgentProfile

internal object BinaryResolver {
    fun resolve(
        profile: AgentProfile,
        settingsPath: String,
        agentellijBin: String?,
        agentSpecificEnv: (String) -> String?,
        discoverBinary: (String) -> String?,
        canExecute: (String) -> Boolean,
        onDiscovered: (String) -> Unit = {}
    ): String {
        settingsPath.trim().takeIf { it.isNotEmpty() && canExecute(it) }?.let { return it }

        agentellijBin?.trim()?.takeIf { it.isNotEmpty() && canExecute(it) }?.let { return it }

        for (envVar in profile.binaryEnvVars) {
            agentSpecificEnv(envVar)?.trim()?.takeIf { it.isNotEmpty() && canExecute(it) }?.let { return it }
        }

        val discoveredBinary = discoverBinary(profile.defaultBinary)
        if ((settingsPath.isBlank() || profile.id == "codex") && discoveredBinary != null) {
            onDiscovered(discoveredBinary)
            return discoveredBinary
        }

        return profile.defaultBinary
    }
}
