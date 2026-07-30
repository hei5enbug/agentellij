package com.agentellij.core.settings

import com.agentellij.core.agent.ClaudeCodeProfile
import com.agentellij.core.agent.CodexCliProfile
import com.agentellij.core.agent.OpenCodeProfile
import com.agentellij.core.agent.TerminalProfile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class AgentProfileResolverSpec : BehaviorSpec({

    val profiles = listOf(OpenCodeProfile(), ClaudeCodeProfile(), CodexCliProfile(), TerminalProfile())

    Given("settings and environment values that point at an agent") {

        When("the stored active agent identifier is known") {
            Then("it wins over every binary path hint") {
                ActiveAgentSelector.resolveProfile(
                    activeAgentId = "claude",
                    settingsPath = "/usr/local/bin/opencode",
                    agentellijBin = "/usr/local/bin/opencode",
                    profiles = profiles
                ).id shouldBe "claude"
            }
        }

        When("the active agent identifier is unknown") {
            Then("the settings binary filename selects the profile") {
                ActiveAgentSelector.resolveProfile(
                    activeAgentId = "unknown",
                    settingsPath = "/opt/bin/claude",
                    agentellijBin = null,
                    profiles = profiles
                ).id shouldBe "claude"
            }

            Then("a codex binary filename selects the codex profile") {
                ActiveAgentSelector.resolveProfile(
                    activeAgentId = "unknown",
                    settingsPath = "/opt/bin/codex",
                    agentellijBin = null,
                    profiles = profiles
                ).id shouldBe "codex"
            }

            Then("the plugin environment variable is used when the settings path is blank") {
                ActiveAgentSelector.resolveProfile(
                    activeAgentId = "unknown",
                    settingsPath = "",
                    agentellijBin = "/opt/bin/claude",
                    profiles = profiles
                ).id shouldBe "claude"
            }

            Then("an unrecognised binary falls back to the first profile") {
                ActiveAgentSelector.resolveProfile(
                    activeAgentId = "unknown",
                    settingsPath = "/opt/bin/not-agentellij",
                    agentellijBin = null,
                    profiles = profiles
                ).id shouldBe "opencode"
            }
        }
    }
})
