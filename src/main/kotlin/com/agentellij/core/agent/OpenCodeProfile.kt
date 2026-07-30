package com.agentellij.core.agent

import com.agentellij.core.text.CustomArgsParser


/**
 * Agent profile for OpenCode.
 *
 * Launch: TUI `opencode [args...]`; GUI `opencode serve [args...]`.
 * State:  `~/.local/state/opencode/` (kv.json, model.json, settings.json)
 */
class OpenCodeProfile : AgentProfile {
    override val id = "opencode"
    override val displayName = "OpenCode"
    override val defaultBinary = "opencode"
    override val binaryEnvVars = listOf("OPENCODE_BIN")
    override val supportedModes = listOf("tui", "gui")

    override fun buildLaunchArgs(binary: String, customArgs: String, mode: String): List<String> {
        val args = mutableListOf(binary)
        if (mode.equals("gui", ignoreCase = true)) {
            args += "serve"
        }
        if (customArgs.isNotBlank()) {
            args.addAll(CustomArgsParser.parse(customArgs))
        }
        return args
    }

    override fun buildInstallCommand(isWindows: Boolean): List<String> =
        npmInstallGlobalCommand("opencode-ai", isWindows)

    override val installCommandLabel = "npm install -g opencode-ai"

    override val serverUrlPattern = Regex(
        "(?:server\\s+)?listening\\s+on\\s+(https?://\\S+)",
        RegexOption.IGNORE_CASE
    )

    override val stateDirectoryName = "opencode"
}
