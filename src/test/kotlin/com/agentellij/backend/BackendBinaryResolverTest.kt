package com.agentellij.backend

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BackendBinaryResolverTest {
    private val profile = OpenCodeProfile()

    @Test
    fun `settings path overrides environment and discovery when executable`() {
        val resolved = BackendBinaryResolver.resolve(
            profile = profile,
            settingsPath = "/custom/opencode",
            agentellijBin = "/env/opencode",
            agentSpecificEnv = { "/agent-env/opencode" },
            discoverBinary = { "/discovered/opencode" },
            canExecute = { it == "/custom/opencode" || it == "/env/opencode" || it == "/agent-env/opencode" }
        )

        assertEquals("/custom/opencode", resolved)
    }

    @Test
    fun `agentellij bin overrides agent specific env and discovery`() {
        val resolved = BackendBinaryResolver.resolve(
            profile = profile,
            settingsPath = "",
            agentellijBin = "/env/opencode",
            agentSpecificEnv = { "/agent-env/opencode" },
            discoverBinary = { "/discovered/opencode" },
            canExecute = { it == "/env/opencode" || it == "/agent-env/opencode" }
        )

        assertEquals("/env/opencode", resolved)
    }

    @Test
    fun `agent specific env is used before discovery`() {
        val resolved = BackendBinaryResolver.resolve(
            profile = profile,
            settingsPath = "",
            agentellijBin = null,
            agentSpecificEnv = { envVar -> if (envVar == "OPENCODE_BIN") "/agent-env/opencode" else null },
            discoverBinary = { "/discovered/opencode" },
            canExecute = { it == "/agent-env/opencode" }
        )

        assertEquals("/agent-env/opencode", resolved)
    }

    @Test
    fun `discovery writes back only when no explicit settings path exists`() {
        val discovered = mutableListOf<String>()

        val resolved = BackendBinaryResolver.resolve(
            profile = profile,
            settingsPath = "",
            agentellijBin = null,
            agentSpecificEnv = { null },
            discoverBinary = { "/discovered/opencode" },
            canExecute = { false },
            onDiscovered = { discovered += it }
        )

        assertEquals("/discovered/opencode", resolved)
        assertEquals(listOf("/discovered/opencode"), discovered)
    }

    @Test
    fun `discovery does not overwrite explicit non executable settings path`() {
        val discovered = mutableListOf<String>()

        val resolved = BackendBinaryResolver.resolve(
            profile = profile,
            settingsPath = "/configured/missing-opencode",
            agentellijBin = null,
            agentSpecificEnv = { null },
            discoverBinary = { "/discovered/opencode" },
            canExecute = { false },
            onDiscovered = { discovered += it }
        )

        assertEquals("opencode", resolved)
        assertEquals(emptyList<String>(), discovered)
    }

    @Test
    fun `default binary is used when no source resolves`() {
        val resolved = BackendBinaryResolver.resolve(
            profile = profile,
            settingsPath = "",
            agentellijBin = null,
            agentSpecificEnv = { null },
            discoverBinary = { null },
            canExecute = { false }
        )

        assertEquals("opencode", resolved)
    }
}
