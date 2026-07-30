package com.agentellij.platform.toolwindow

import com.agentellij.core.agent.AgentCatalog
import com.agentellij.core.agent.AgentProfile
import com.agentellij.core.agent.AgentStateLocation
import com.agentellij.core.settings.ActiveAgentSelector
import com.agentellij.core.settings.AgentModePolicy
import com.agentellij.platform.config.AgentellIJSettings
import java.io.File

/**
 * The one place that joins stored settings, the environment, and the agent catalogue.
 *
 * Everything else asks here rather than reading the settings service, so there is a
 * single answer to "which agent is active" and a single place to look when that answer
 * is wrong.
 *
 * Callers ask each time rather than holding a value. Reading is cheap, and a value read
 * once would go stale the moment the user switches agent or edits the settings panel,
 * which is exactly when these answers matter most.
 *
 * The settings panel is the deliberate exception to this: it owns the settings, so it
 * talks to the service directly.
 */
object AgentellIJWiring {

    fun activeProfile(): AgentProfile {
        val settings = AgentellIJSettings.getInstance()
        val activeAgentId = settings.getActiveAgent()

        return ActiveAgentSelector.resolveProfile(
            activeAgentId = activeAgentId,
            settingsPath = settings.getAgentPath(activeAgentId),
            agentellijBin = System.getenv(PLUGIN_BINARY_VARIABLE),
            profiles = AgentCatalog.allProfiles()
        )
    }

    fun currentMode(): String = AgentellIJSettings.getInstance().getMode()

    fun binaryPathFor(profile: AgentProfile): String =
        AgentellIJSettings.getInstance().getAgentPath(profile.id)

    fun customArgs(): String = AgentellIJSettings.getInstance().state.customArgs

    /** Where the active agent keeps its conversation state, or null when it keeps none. */
    fun agentStateDirectory(): File? = AgentStateLocation.resolve(
        profile = activeProfile(),
        userHome = System.getProperty("user.home").orEmpty(),
        xdgStateHome = System.getenv("XDG_STATE_HOME")
    )

    /** Switches the active agent, correcting the mode if the new agent cannot provide it. */
    fun switchTo(profile: AgentProfile): String {
        val settings = AgentellIJSettings.getInstance()
        settings.state.activeAgent = profile.id
        settings.state.mode = AgentModePolicy.normalizeModeForProfile(settings.getMode(), profile)
        return settings.getMode()
    }

    /** Records the mode the tool window is about to show, corrected for the active agent. */
    fun applyMode(mode: String, profile: AgentProfile): String {
        val normalized = AgentModePolicy.normalizeModeForProfile(mode, profile)
        val settings = AgentellIJSettings.getInstance()
        if (settings.state.mode != normalized) settings.state.mode = normalized
        return normalized
    }

    private const val PLUGIN_BINARY_VARIABLE = "AGENTELLIJ_BIN"
}
