package com.agentellij.settings

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
    fun `agent path accessors keep opencode and claude paths separate`() {
        val settings = AgentellIJSettings()

        settings.setAgentPath("opencode", "/bin/opencode")
        settings.setAgentPath("claude", "/bin/claude")

        assertEquals("/bin/opencode", settings.getAgentPath("opencode"))
        assertEquals("/bin/claude", settings.getAgentPath("claude"))
    }
}
