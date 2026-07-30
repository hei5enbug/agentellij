package com.agentellij.core.agent
/**
 * Defines agent-specific behavior for launching, server detection, and state management.
 *
 * Each supported AI agent (OpenCode, Claude Code, Codex, etc.) implements this interface
 * to provide its own launch command, stdout parsing pattern, and state file layout.
 */
interface AgentProfile {
    /** Unique identifier (e.g. "opencode") */
    val id: String
    /** Human-readable name shown in UI */
    val displayName: String
    /** Default binary name used when no custom path is configured */
    val defaultBinary: String
    /**
     * Agent-specific environment variables to check for the binary path, in priority order.
     * The plugin-level `AGENTELLIJ_BIN` is checked separately before these.
     */
    val binaryEnvVars: List<String>
    /**
     * Build the full command arguments to launch the agent for the requested mode.
     *
     * @param binary resolved binary path
     * @param customArgs user-provided extra arguments (may be blank)
     * @param mode launch mode identifier (for example, `"gui"` or `"tui"`)
     */
    fun buildLaunchArgs(binary: String, customArgs: String, mode: String): List<String>
    /**
     * Build the user-approved install command for this agent, or null when automatic
     * installation is not supported.
     *
     * The host platform is passed in rather than detected here, so a profile stays a
     * plain record that behaves the same wherever it runs.
     */
    fun buildInstallCommand(isWindows: Boolean): List<String>? = null
    /** Human-readable install command shown before the user consents to run it. */
    val installCommandLabel: String?
        get() = null
    /**
     * UI modes this agent supports (e.g. `["tui"]` or `["tui", "gui"]`).
     */
    val supportedModes: List<String>
    /**
     * Regex to detect the server URL from agent stdout.
     * Capture group 1 must contain the full URL (e.g. `http://localhost:3000`).
     */
    val serverUrlPattern: Regex
    /**
     * Name of the directory holding this agent's state files (kv, model, settings), or
     * null when the agent keeps no state on disk.
     *
     * Only the name lives here. Turning it into a full path needs the user's home
     * directory and an environment variable, which a plain record must not read.
     */
    val stateDirectoryName: String?
    /** When true, this "agent" simply opens the IDE's native default interactive shell (no binary, no launch args). */
    val usesDefaultShell: Boolean get() = false
}
internal fun npmInstallGlobalCommand(packageName: String, isWindows: Boolean): List<String> =
    if (isWindows) listOf("cmd", "/c", "npm", "install", "-g", packageName)
    else listOf("npm", "install", "-g", packageName)
