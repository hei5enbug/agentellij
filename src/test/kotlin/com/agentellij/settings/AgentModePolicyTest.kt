package com.agentellij.settings

import com.agentellij.backend.AgentProfile
import com.agentellij.backend.ClaudeCodeProfile
import com.agentellij.backend.CodexCliProfile
import com.agentellij.backend.OpenCodeProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class AgentModePolicyTest {
    @Test
    fun `opencode keeps gui mode because it supports gui`() {
        val mode = AgentModePolicy.normalizeModeForProfile("gui", OpenCodeProfile())

        assertEquals("gui", mode)
    }

    @Test
    fun `claude gui mode normalizes to tui`() {
        val mode = AgentModePolicy.normalizeModeForProfile("gui", ClaudeCodeProfile())

        assertEquals("tui", mode)
    }

    @Test
    fun `codex gui mode normalizes to tui`() {
        val mode = AgentModePolicy.normalizeModeForProfile("gui", CodexCliProfile())

        assertEquals("tui", mode)
    }

    @Test
    fun `invalid mode normalizes to tui when tui is supported`() {
        val mode = AgentModePolicy.normalizeModeForProfile("browser", OpenCodeProfile())

        assertEquals("tui", mode)
    }

    @Test
    fun `empty supported modes still falls back to default tui`() {
        val mode = AgentModePolicy.normalizeModeForProfile("gui", FakeProfile(supportedModes = emptyList()))

        assertEquals("tui", mode)
    }

    @Test
    fun `unknown agent id resolves to default opencode profile`() {
        val profile = AgentModePolicy.resolveProfile(
            agentId = "missing-agent",
            profiles = listOf(ClaudeCodeProfile(), OpenCodeProfile())
        )

        assertEquals("opencode", profile.id)
    }

    @Test
    fun `blank agent id resolves to default opencode profile`() {
        val profile = AgentModePolicy.resolveProfile(
            agentId = " ",
            profiles = listOf(ClaudeCodeProfile(), OpenCodeProfile())
        )

        assertEquals("opencode", profile.id)
    }

    private class FakeProfile(
        override val supportedModes: List<String>
    ) : AgentProfile {
        override val id: String = "fake"
        override val displayName: String = "Fake"
        override val defaultBinary: String = "fake"
        override val binaryEnvVars: List<String> = emptyList()
        override val serverUrlPattern: Regex = Regex("fake")
        override val statePath: File? = null

        override fun buildLaunchArgs(binary: String, customArgs: String, mode: String): List<String> =
            listOf(binary)
    }
}
