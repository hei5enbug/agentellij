package com.agentellij.core.launch

import com.agentellij.core.agent.AgentProfile

/**
 * Works out which commands to try when starting an agent.
 *
 * Custom arguments come from the user and can be wrong, so a launch that fails with
 * them is retried without them. That retry only makes sense when there were custom
 * arguments in the first place: otherwise the second attempt would be identical to the
 * first and the agent would simply be started twice.
 */
internal object LaunchCommandPlanner {

    /** Commands to try in order. Later attempts are only used if earlier ones fail. */
    fun attempts(profile: AgentProfile, mode: String, binary: String, customArgs: String): List<List<String>> {
        val trimmed = customArgs.trim()
        val withCustomArgs = profile.buildLaunchArgs(binary, trimmed, mode)
        if (trimmed.isEmpty()) return listOf(withCustomArgs)

        return listOf(withCustomArgs, profile.buildLaunchArgs(binary, "", mode))
    }
}
