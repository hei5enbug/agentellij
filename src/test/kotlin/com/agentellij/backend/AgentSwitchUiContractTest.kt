package com.agentellij.backend

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentSwitchUiContractTest {
    private val profiles = AgentProfileResolver.allProfiles()

    @Test
    fun `agent select box lists opencode claude codex in order`() {
        assertEquals(listOf("opencode", "claude", "codex", "terminal"), profiles.map { it.id })
    }

    @Test
    fun `agent select box shows expected display names`() {
        assertEquals(listOf("OpenCode", "Claude Code", "Codex CLI", "Terminal"), profiles.map { it.displayName })
    }

    @Test
    fun `only opencode enables the gui mode toggle`() {
        val guiToggleEnabled = profiles.associate { it.id to (it.supportedModes.size > 1) }

        assertTrue(guiToggleEnabled.getValue("opencode"))
        assertFalse(guiToggleEnabled.getValue("claude"))
        assertFalse(guiToggleEnabled.getValue("codex"))
        assertFalse(guiToggleEnabled.getValue("terminal"))
    }
}
