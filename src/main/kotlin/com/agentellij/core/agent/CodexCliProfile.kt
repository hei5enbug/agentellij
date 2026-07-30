package com.agentellij.core.agent

import com.agentellij.core.text.CustomArgsParser


/**
 * Agent profile for OpenAI Codex CLI.
 *
 * Launch (TUI): `codex \[args...]`
 * GUI mode is not supported by AgentellIJ yet — Codex CLI runs as a terminal agent here.
 */
class CodexCliProfile : AgentProfile {
    override val id = "codex"
    override val displayName = "Codex CLI"
    override val defaultBinary = "codex"
    override val binaryEnvVars = listOf("CODEX_BIN")
    override val supportedModes = listOf("tui")

    override fun buildLaunchArgs(binary: String, customArgs: String, mode: String): List<String> {
        val args = mutableListOf(binary)
        if (customArgs.isNotBlank()) {
            args.addAll(CustomArgsParser.parse(customArgs))
        }
        return args
    }

    override fun buildInstallCommand(isWindows: Boolean): List<String> =
        npmInstallGlobalCommand("@openai/codex", isWindows)

    override val installCommandLabel = "npm install -g @openai/codex"

    override val serverUrlPattern: Regex = Regex("(?!x)x")

    override val stateDirectoryName: String? = null
}
