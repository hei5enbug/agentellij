package com.agentellij.core.settings

/**
 * The binary path remembered for each agent.
 *
 * Agents are kept apart on purpose: pointing OpenCode at a custom build should not
 * disturb where Claude Code is found. The Terminal agent runs the IDE's own shell and
 * has no binary, so it always reads back as empty and cannot be given one.
 */
data class AgentPaths(
    val shared: String = "",
    val claude: String = "",
    val codex: String = ""
) {
    fun pathFor(agentId: String): String = when (agentId) {
        CLAUDE_ID -> claude
        CODEX_ID -> codex
        TERMINAL_ID -> ""
        else -> shared
    }

    fun withPath(agentId: String, path: String): AgentPaths = when (agentId) {
        CLAUDE_ID -> copy(claude = path)
        CODEX_ID -> copy(codex = path)
        TERMINAL_ID -> this
        else -> copy(shared = path)
    }

    companion object {
        const val CLAUDE_ID = "claude"
        const val CODEX_ID = "codex"
        const val TERMINAL_ID = "terminal"
    }
}
