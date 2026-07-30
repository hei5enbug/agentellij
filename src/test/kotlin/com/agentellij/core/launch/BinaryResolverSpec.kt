package com.agentellij.core.launch

import com.agentellij.core.agent.CodexCliProfile
import com.agentellij.core.agent.OpenCodeProfile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class BackendBinaryResolverSpec : BehaviorSpec({

    val profile = OpenCodeProfile()

    Given("several places that could name the agent binary") {

        When("the settings path points at an executable file") {
            Then("the settings path wins over the environment and discovery") {
                BinaryResolver.resolve(
                    profile = profile,
                    settingsPath = "/custom/opencode",
                    agentellijBin = "/env/opencode",
                    agentSpecificEnv = { "/agent-env/opencode" },
                    discoverBinary = { "/discovered/opencode" },
                    canExecute = { it in setOf("/custom/opencode", "/env/opencode", "/agent-env/opencode") }
                ) shouldBe "/custom/opencode"
            }
        }

        When("the settings path is blank") {
            Then("the plugin environment variable wins over the agent one") {
                BinaryResolver.resolve(
                    profile = profile,
                    settingsPath = "",
                    agentellijBin = "/env/opencode",
                    agentSpecificEnv = { "/agent-env/opencode" },
                    discoverBinary = { "/discovered/opencode" },
                    canExecute = { it in setOf("/env/opencode", "/agent-env/opencode") }
                ) shouldBe "/env/opencode"
            }

            Then("the agent environment variable wins over discovery") {
                BinaryResolver.resolve(
                    profile = profile,
                    settingsPath = "",
                    agentellijBin = null,
                    agentSpecificEnv = { if (it == "OPENCODE_BIN") "/agent-env/opencode" else null },
                    discoverBinary = { "/discovered/opencode" },
                    canExecute = { it == "/agent-env/opencode" }
                ) shouldBe "/agent-env/opencode"
            }

            Then("the codex agent environment variable behaves the same way") {
                BinaryResolver.resolve(
                    profile = CodexCliProfile(),
                    settingsPath = "",
                    agentellijBin = null,
                    agentSpecificEnv = { if (it == "CODEX_BIN") "/agent-env/codex" else null },
                    discoverBinary = { "/discovered/codex" },
                    canExecute = { it == "/agent-env/codex" }
                ) shouldBe "/agent-env/codex"
            }

            Then("discovery is accepted and reported back") {
                val discovered = mutableListOf<String>()

                val resolved = BinaryResolver.resolve(
                    profile = profile,
                    settingsPath = "",
                    agentellijBin = null,
                    agentSpecificEnv = { null },
                    discoverBinary = { "/discovered/opencode" },
                    canExecute = { false },
                    onDiscovered = { discovered += it }
                )

                resolved shouldBe "/discovered/opencode"
                discovered shouldBe listOf("/discovered/opencode")
            }

            Then("the default binary name is the last resort") {
                BinaryResolver.resolve(
                    profile = profile,
                    settingsPath = "",
                    agentellijBin = null,
                    agentSpecificEnv = { null },
                    discoverBinary = { null },
                    canExecute = { false }
                ) shouldBe "opencode"
            }
        }

        When("the settings path names a file that cannot be executed") {
            Then("discovery does not silently replace the configured path") {
                val discovered = mutableListOf<String>()

                val resolved = BinaryResolver.resolve(
                    profile = profile,
                    settingsPath = "/configured/missing-opencode",
                    agentellijBin = null,
                    agentSpecificEnv = { null },
                    discoverBinary = { "/discovered/opencode" },
                    canExecute = { false },
                    onDiscovered = { discovered += it }
                )

                resolved shouldBe "opencode"
                discovered shouldBe emptyList()
            }

            Then("codex is the deliberate exception and does adopt the discovered path") {
                val discovered = mutableListOf<String>()

                val resolved = BinaryResolver.resolve(
                    profile = CodexCliProfile(),
                    settingsPath = "/configured/missing-codex",
                    agentellijBin = null,
                    agentSpecificEnv = { null },
                    discoverBinary = { if (it == "codex") "/usr/local/bin/codex" else null },
                    canExecute = { false },
                    onDiscovered = { discovered += it }
                )

                resolved shouldBe "/usr/local/bin/codex"
                discovered shouldBe listOf("/usr/local/bin/codex")
            }
        }
    }
})
