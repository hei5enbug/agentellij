package com.agentellij.backend

import java.io.File

/**
 * Agent profile for [Claude Code](https://docs.anthropic.com/en/docs/claude-code).
 *
 * Launch (TUI): `claude \[args...]`
 * GUI mode is not supported — Claude Code is a terminal-only tool.
 */
class ClaudeCodeProfile : AgentProfile {
    override val id = "claude"
    override val displayName = "Claude Code"
    override val defaultBinary = "claude"
    override val binaryEnvVars = listOf("CLAUDE_CODE_BIN")
    override val supportedModes = listOf("tui")

    override fun buildLaunchArgs(binary: String, customArgs: String, mode: String): List<String> {
        val args = mutableListOf(binary)
        if (customArgs.isNotBlank()) {
            args.addAll(CustomArgsParser.parse(customArgs))
        }
        return args
    }

    override fun buildInstallCommand(isWindows: Boolean): List<String> =
        npmInstallGlobalCommand("@anthropic-ai/claude-code", isWindows)

    override val installCommandLabel = "npm install -g @anthropic-ai/claude-code"

    override val serverUrlPattern: Regex = Regex("(?!x)x")

    override val statePath: File? = null
}
