package com.agentellij.core.agent

import java.io.File

/**
 * Works out where an agent keeps its state files.
 *
 * The values it needs are supplied by the caller rather than read here, so the rule can
 * be checked without depending on the machine the test happens to run on.
 */
object AgentStateLocation {
    private const val DEFAULT_RELATIVE_BASE = ".local/state"

    /**
     * @param xdgStateHome the `XDG_STATE_HOME` value, or null when it is not set.
     *   An empty string counts as a value, not as absent. That is the behaviour the
     *   plugin has always had, and it resolves to the filesystem root rather than to
     *   the home directory, so `XDG_STATE_HOME=""` puts agent state in `/opencode`.
     *   Treating an empty value as absent would move an existing user's state, so it
     *   is preserved deliberately.
     */
    fun resolve(profile: AgentProfile, userHome: String, xdgStateHome: String?): File? {
        val directoryName = profile.stateDirectoryName ?: return null
        val base = xdgStateHome ?: "$userHome/$DEFAULT_RELATIVE_BASE"
        return File(base, directoryName)
    }
}
