package com.agentellij.backend

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AgentProfileResolverTest {
    private val profiles = listOf(OpenCodeProfile(), ClaudeCodeProfile(), CodexCliProfile(), TerminalProfile())

    @Test
    fun `active agent id wins over binary path hints`() {
        val profile = AgentProfileResolver.resolveProfile(
            activeAgentId = "claude",
            settingsPath = "/usr/local/bin/opencode",
            agentellijBin = "/usr/local/bin/opencode",
            profiles = profiles
        )

        assertEquals("claude", profile.id)
    }

    @Test
    fun `production profile registry includes codex and terminal`() {
        val profileIds = AgentProfileResolver.allProfiles().map { it.id }

        assertEquals(listOf("opencode", "claude", "codex", "terminal"), profileIds)
    }

    @Test
    fun `legacy settings path filename selects matching profile when active id is unknown`() {
        val profile = AgentProfileResolver.resolveProfile(
            activeAgentId = "unknown",
            settingsPath = "/opt/bin/claude",
            agentellijBin = null,
            profiles = profiles
        )

        assertEquals("claude", profile.id)
    }

    @Test
    fun `agentellij bin filename selects matching profile when settings path is blank`() {
        val profile = AgentProfileResolver.resolveProfile(
            activeAgentId = "unknown",
            settingsPath = "",
            agentellijBin = "/opt/bin/claude",
            profiles = profiles
        )

        assertEquals("claude", profile.id)
    }

    @Test
    fun `codex settings path filename selects codex profile when active id is unknown`() {
        val profile = AgentProfileResolver.resolveProfile(
            activeAgentId = "unknown",
            settingsPath = "/opt/bin/codex",
            agentellijBin = null,
            profiles = profiles
        )

        assertEquals("codex", profile.id)
    }

    @Test
    fun `unknown agent and unknown binary fallback to first profile`() {
        val profile = AgentProfileResolver.resolveProfile(
            activeAgentId = "unknown",
            settingsPath = "/opt/bin/not-agentellij",
            agentellijBin = null,
            profiles = profiles
        )

        assertEquals("opencode", profile.id)
    }
}
