package com.agentellij.backend

import java.io.File

/**
 * Agent profile for [OpenCode](https://github.com/sst/opencode).
 *
 * Launch: `opencode serve [args...]`
 * State:  `~/.local/state/opencode/` (kv.json, model.json, settings.json)
 */
class OpenCodeProfile : AgentProfile {
    override val id = "opencode"
    override val displayName = "OpenCode"
    override val defaultBinary = "opencode"
    override val binaryEnvVars = listOf("OPENCODE_BIN")

    override fun buildLaunchArgs(binary: String, customArgs: String): List<String> {
        val args = mutableListOf(binary, "serve")
        if (customArgs.isNotBlank()) {
            args.addAll(customArgs.split(" ").filter { it.isNotBlank() })
        }
        return args
    }

    override val serverUrlPattern = Regex(
        "(?:server\\s+)?listening\\s+on\\s+(https?://\\S+)",
        RegexOption.IGNORE_CASE
    )

    override val statePath: File
        get() = File(
            System.getenv("XDG_STATE_HOME") ?: "${System.getProperty("user.home")}/.local/state",
            "opencode"
        )
}
