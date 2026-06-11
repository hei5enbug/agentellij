package com.agentellij.backend

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AgentProfileLaunchArgsTest {
    @Test
    fun `opencode gui launch args include serve`() {
        val args = OpenCodeProfile().buildLaunchArgs("opencode", "--flag value", "gui")

        assertEquals(listOf("opencode", "serve", "--flag", "value"), args)
    }

    @Test
    fun `opencode tui launch args do not include serve`() {
        val args = OpenCodeProfile().buildLaunchArgs("opencode", "--flag", "tui")

        assertEquals(listOf("opencode", "--flag"), args)
    }

    @Test
    fun `claude launch args never include gui serve command`() {
        val args = ClaudeCodeProfile().buildLaunchArgs("claude", "--dangerously-skip-permissions", "gui")

        assertEquals(listOf("claude", "--dangerously-skip-permissions"), args)
    }

    @Test
    fun `codex launch args never include gui serve command`() {
        val args = CodexCliProfile().buildLaunchArgs("codex", "--model gpt-5", "gui")

        assertEquals(listOf("codex", "--model", "gpt-5"), args)
    }

    @Test
    fun `profiles expose user-approved npm install commands`() {
        assertEquals(listOf("npm", "install", "-g", "opencode-ai"), OpenCodeProfile().buildInstallCommand(isWindows = false))
        assertEquals(
            listOf("npm", "install", "-g", "@anthropic-ai/claude-code"),
            ClaudeCodeProfile().buildInstallCommand(isWindows = false)
        )
        assertEquals(listOf("npm", "install", "-g", "@openai/codex"), CodexCliProfile().buildInstallCommand(isWindows = false))
    }

    @Test
    fun `windows npm install command runs through cmd`() {
        assertEquals(
            listOf("cmd", "/c", "npm", "install", "-g", "@openai/codex"),
            CodexCliProfile().buildInstallCommand(isWindows = true)
        )
    }

    @Test
    fun `custom args parser preserves quoted paths with spaces`() {
        val args = OpenCodeProfile().buildLaunchArgs("opencode", "--config \"/Users/me/My Config/opencode.json\"", "tui")

        assertEquals(listOf("opencode", "--config", "/Users/me/My Config/opencode.json"), args)
    }

    @Test
    fun `custom args parser handles single quotes and escaped spaces`() {
        val args = ClaudeCodeProfile().buildLaunchArgs("claude", "--name 'Claude Code' --path /tmp/my\\ file", "tui")

        assertEquals(listOf("claude", "--name", "Claude Code", "--path", "/tmp/my file"), args)
    }

    @Test
    fun `custom args parser collapses extra whitespace`() {
        val args = OpenCodeProfile().buildLaunchArgs("opencode", "   --one    --two\tvalue   ", "tui")

        assertEquals(listOf("opencode", "--one", "--two", "value"), args)
    }

    @Test
    fun `custom args parser preserves empty quoted argument`() {
        val args = ClaudeCodeProfile().buildLaunchArgs("claude", "--name \"\" --fallback ''", "tui")

        assertEquals(listOf("claude", "--name", "", "--fallback", ""), args)
    }
}
