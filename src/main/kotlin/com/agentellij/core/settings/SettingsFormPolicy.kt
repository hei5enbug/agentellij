package com.agentellij.core.settings

import com.agentellij.core.agent.AgentProfile
import com.agentellij.core.settings.AgentModePolicy

/** What the settings panel currently shows, or what is currently stored. */
internal data class SettingsFormValues(
    val agentId: String,
    val mode: String,
    val agentPaths: Map<String, String>,
    val customArgs: String
)

/**
 * The decisions the settings panel makes, separated from the Swing components that
 * display them.
 */
internal object SettingsFormPolicy {
    const val GUI_MODE_LABEL = "GUI mode — embedded web UI"
    const val TUI_MODE_LABEL = "TUI mode — terminal wrapper"

    fun modeFromLabel(label: String?): String = if (label == TUI_MODE_LABEL) "tui" else "gui"

    fun labelForMode(mode: String): String =
        if (AgentModePolicy.normalizeMode(mode) == "tui") TUI_MODE_LABEL else GUI_MODE_LABEL

    /**
     * Compared before trimming, because the Apply button should light up as soon as the
     * text differs from what is stored, even if the difference is only whitespace that
     * saving would remove.
     */
    fun isModified(shown: SettingsFormValues, stored: SettingsFormValues): Boolean =
        shown.agentId != stored.agentId ||
            shown.mode != stored.mode ||
            shown.agentPaths.any { (agentId, path) -> path != stored.agentPaths[agentId].orEmpty() } ||
            shown.customArgs != stored.customArgs

    /** Applies the rules that run on the way into storage. */
    fun normalizeForSave(shown: SettingsFormValues, profile: AgentProfile): SettingsFormValues =
        shown.copy(
            mode = AgentModePolicy.normalizeModeForProfile(shown.mode, profile),
            agentPaths = shown.agentPaths.mapValues { (_, path) -> path.trim() },
            customArgs = shown.customArgs.trim()
        )

    /** The Terminal agent has no binary, so its path field is emptied and locked. */
    fun pathFieldEnabled(profile: AgentProfile): Boolean = !profile.usesDefaultShell
}
