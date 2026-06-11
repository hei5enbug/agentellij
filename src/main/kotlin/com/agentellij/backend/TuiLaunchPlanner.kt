package com.agentellij.backend

data class TuiLaunchPlan(val installed: Boolean, val command: List<String>)

object TuiLaunchPlanner {
    fun plan(
        profile: AgentProfile,
        settingsPath: String,
        customArgs: String,
        agentellijBin: String?,
        agentSpecificEnv: (String) -> String?,
        discoverBinary: (String) -> String?,
        canExecute: (String) -> Boolean
    ): TuiLaunchPlan {
        val settingsCandidate = settingsPath.trim()
        if (settingsCandidate.isNotEmpty() && canExecute(settingsCandidate)) {
            return launchPlan(profile, settingsCandidate, customArgs, installed = true)
        }

        val agentellijCandidate = agentellijBin?.trim().orEmpty()
        if (agentellijCandidate.isNotEmpty() && canExecute(agentellijCandidate)) {
            return launchPlan(profile, agentellijCandidate, customArgs, installed = true)
        }

        for (envVar in profile.binaryEnvVars) {
            val envCandidate = agentSpecificEnv(envVar)?.trim().orEmpty()
            if (envCandidate.isNotEmpty() && canExecute(envCandidate)) {
                return launchPlan(profile, envCandidate, customArgs, installed = true)
            }
        }

        if (discoverBinary(profile.defaultBinary) != null) {
            return launchPlan(profile, profile.defaultBinary, customArgs, installed = true)
        }

        return launchPlan(profile, profile.defaultBinary, customArgs, installed = false)
    }

    private fun launchPlan(
        profile: AgentProfile,
        binary: String,
        customArgs: String,
        installed: Boolean
    ): TuiLaunchPlan = TuiLaunchPlan(
        installed = installed,
        command = profile.buildLaunchArgs(binary, customArgs, "tui")
    )
}
