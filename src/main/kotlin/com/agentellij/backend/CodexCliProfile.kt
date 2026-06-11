package com.agentellij.backend

import java.io.File

/**
 * Agent profile for [OpenAI Codex CLI](https://github.com/openai/codex).
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

    override val serverUrlPattern: Regex = Regex("(?!x)x")

    override val statePath: File? = null
}
