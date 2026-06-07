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
