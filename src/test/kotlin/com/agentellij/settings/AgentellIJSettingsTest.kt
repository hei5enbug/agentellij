package com.agentellij.settings

import com.agentellij.backend.ClaudeCodeProfile
import com.agentellij.backend.CodexCliProfile
import com.agentellij.backend.OpenCodeProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AgentellIJSettingsTest {
    @Test
    fun `loadState normalizes invalid mode to tui`() {
        val settings = AgentellIJSettings()

        settings.loadState(AgentellIJSettings.State(mode = "invalid"))

        assertEquals("tui", settings.getMode())
    }

    @Test
    fun `getActiveAgent falls back to opencode when blank`() {
        val settings = AgentellIJSettings()

        settings.loadState(AgentellIJSettings.State(activeAgent = ""))

        assertEquals("opencode", settings.getActiveAgent())
    }

    @Test
    fun `agent path accessors keep opencode claude and codex paths separate`() {
        val settings = AgentellIJSettings()

        settings.setAgentPath("opencode", "/bin/opencode")
        settings.setAgentPath("claude", "/bin/claude")
        settings.setAgentPath("codex", "/bin/codex")

        assertEquals("/bin/opencode", settings.getAgentPath("opencode"))
        assertEquals("/bin/claude", settings.getAgentPath("claude"))
        assertEquals("/bin/codex", settings.getAgentPath("codex"))
    }

    @Test
    fun `agent path selection state preserves per-agent edits while switching`() {
        val state = AgentPathSelectionState(
            profiles = listOf(OpenCodeProfile(), ClaudeCodeProfile(), CodexCliProfile()),
            selectedAgentId = "opencode",
            pathProvider = { agentId -> "/bin/$agentId" }
        )

        val codexPath = state.selectAgent(agentId = "codex", currentPath = "/custom/opencode")
        val claudePath = state.selectAgent(agentId = "claude", currentPath = "/custom/codex")
        val paths = state.snapshot(selectedAgentId = "claude", currentPath = "/custom/claude")

        assertEquals("/bin/codex", codexPath)
        assertEquals("/bin/claude", claudePath)
        assertEquals("/custom/opencode", paths["opencode"])
        assertEquals("/custom/claude", paths["claude"])
        assertEquals("/custom/codex", paths["codex"])
    }

    @Test
    fun `terminal agent path is a no-op and never corrupts other agent paths`() {
        val settings = AgentellIJSettings()
        settings.setAgentPath("opencode", "/bin/opencode")
        settings.setAgentPath("terminal", "/bin/should-be-ignored")
        assertEquals("", settings.getAgentPath("terminal"))
        assertEquals("/bin/opencode", settings.getAgentPath("opencode"))
    }
}
