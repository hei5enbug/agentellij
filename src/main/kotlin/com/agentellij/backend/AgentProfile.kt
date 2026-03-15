package com.agentellij.backend

import java.io.File

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
     * Build the full command arguments to launch the agent.
     * @param binary resolved binary path
     * @param customArgs user-provided extra arguments (may be blank)
     * @return full argument list (e.g. ["opencode", "serve", "--port", "3000"])
     */
    fun buildLaunchArgs(binary: String, customArgs: String): List<String>

    /**
     * Regex to detect the server URL from agent stdout.
     * Capture group 1 must contain the full URL (e.g. `http://localhost:3000`).
     */
    val serverUrlPattern: Regex

    /**
     * Base directory for agent state files (kv, model, settings), or null if the agent
     * does not use file-based state management.
     */
    val statePath: File?
}
