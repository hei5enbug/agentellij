package com.agentellij.backend

import java.io.File

/**
 * Agent profile that opens IntelliJ's native persistent interactive shell in the tool window.
 *
 * Not a CLI agent: no binary, no install, no server URL, no state. TUI-only.
 */
class TerminalProfile : AgentProfile {
    override val id = "terminal"
    override val displayName = "Terminal"
    override val defaultBinary = ""
    override val binaryEnvVars = emptyList<String>()
    override val supportedModes = listOf("tui")
    override val usesDefaultShell = true

    override fun buildLaunchArgs(binary: String, customArgs: String, mode: String): List<String> = emptyList()

    override val serverUrlPattern: Regex = Regex("(?!x)x")

    override val statePath: File? = null
}
