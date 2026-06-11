package com.agentellij.backend

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TuiLaunchPlannerTest {
    private val profile = OpenCodeProfile()

    @Test
    fun `settings path overrides environment and discovery when executable`() {
        val plan = TuiLaunchPlanner.plan(
            profile = profile,
            settingsPath = " /custom/opencode ",
            customArgs = "--model 'gpt 5'",
            agentellijBin = "/env/opencode",
            agentSpecificEnv = { "/agent-env/opencode" },
            discoverBinary = { "/discovered/opencode" },
            canExecute = { it == "/custom/opencode" || it == "/env/opencode" || it == "/agent-env/opencode" }
        )

        assertTrue(plan.installed)
        assertEquals(listOf("/custom/opencode", "--model", "gpt 5"), plan.command)
    }

    @Test
    fun `agentellij bin overrides agent specific env and discovery`() {
        val plan = TuiLaunchPlanner.plan(
            profile = profile,
            settingsPath = "",
            customArgs = "",
            agentellijBin = " /env/opencode ",
            agentSpecificEnv = { "/agent-env/opencode" },
            discoverBinary = { "/discovered/opencode" },
            canExecute = { it == "/env/opencode" || it == "/agent-env/opencode" }
        )

        assertTrue(plan.installed)
        assertEquals(listOf("/env/opencode"), plan.command)
    }

    @Test
    fun `agent specific env is used before discovery`() {
        val plan = TuiLaunchPlanner.plan(
            profile = CodexCliProfile(),
            settingsPath = "",
            customArgs = "--approval-mode full-auto",
            agentellijBin = null,
            agentSpecificEnv = { envVar -> if (envVar == "CODEX_BIN") " /agent-env/codex " else null },
            discoverBinary = { "/discovered/codex" },
            canExecute = { it == "/agent-env/codex" }
        )

        assertTrue(plan.installed)
        assertEquals(listOf("/agent-env/codex", "--approval-mode", "full-auto"), plan.command)
    }

    @Test
    fun `discovery marks installed but launches bare default binary`() {
        val plan = TuiLaunchPlanner.plan(
            profile = profile,
            settingsPath = "",
            customArgs = "--flag",
            agentellijBin = null,
            agentSpecificEnv = { null },
            discoverBinary = { binaryName -> if (binaryName == "opencode") "/Users/me/.opencode/bin/opencode" else null },
            canExecute = { false }
        )

        assertTrue(plan.installed)
        assertEquals(listOf("opencode", "--flag"), plan.command)
    }

    @Test
    fun `missing binary reports not installed and keeps default command`() {
        val plan = TuiLaunchPlanner.plan(
            profile = ClaudeCodeProfile(),
            settingsPath = "/configured/missing-claude",
            customArgs = "--dangerously-skip-permissions",
            agentellijBin = null,
            agentSpecificEnv = { null },
            discoverBinary = { null },
            canExecute = { false }
        )

        assertFalse(plan.installed)
        assertEquals(listOf("claude", "--dangerously-skip-permissions"), plan.command)
    }

    @Test
    fun `tui plan never adds gui serve argument`() {
        val plan = TuiLaunchPlanner.plan(
            profile = profile,
            settingsPath = "/custom/opencode",
            customArgs = "",
            agentellijBin = null,
            agentSpecificEnv = { null },
            discoverBinary = { null },
            canExecute = { it == "/custom/opencode" }
        )

        assertEquals(listOf("/custom/opencode"), plan.command)
    }
}
