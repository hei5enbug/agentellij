package com.agentellij.core.settings

import com.agentellij.core.agent.AgentProfile
import com.agentellij.core.agent.ClaudeCodeProfile
import com.agentellij.core.agent.CodexCliProfile
import com.agentellij.core.agent.OpenCodeProfile
import com.agentellij.core.agent.TerminalProfile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

private class FixedModesProfile(
    override val supportedModes: List<String>
) : AgentProfile {
    override val id: String = "fake"
    override val displayName: String = "Fake"
    override val defaultBinary: String = "fake"
    override val binaryEnvVars: List<String> = emptyList()
    override val serverUrlPattern: Regex = Regex("fake")
    override val stateDirectoryName: String? = null

    override fun buildLaunchArgs(binary: String, customArgs: String, mode: String): List<String> =
        listOf(binary)
}

class AgentModePolicySpec : BehaviorSpec({

    Given("a requested mode and the agent that has to honour it") {

        When("the agent supports the requested mode") {
            Then("OpenCode keeps gui mode") {
                AgentModePolicy.normalizeModeForProfile("gui", OpenCodeProfile()) shouldBe "gui"
            }
        }

        When("the agent is terminal-only") {
            Then("Claude Code falls back to tui") {
                AgentModePolicy.normalizeModeForProfile("gui", ClaudeCodeProfile()) shouldBe "tui"
            }

            Then("Codex CLI falls back to tui") {
                AgentModePolicy.normalizeModeForProfile("gui", CodexCliProfile()) shouldBe "tui"
            }

            Then("the Terminal agent falls back to tui") {
                AgentModePolicy.normalizeModeForProfile("gui", TerminalProfile()) shouldBe "tui"
            }
        }

        When("the requested mode is not a mode at all") {
            Then("it falls back to tui even for an agent that supports gui") {
                AgentModePolicy.normalizeModeForProfile("browser", OpenCodeProfile()) shouldBe "tui"
            }
        }

        When("the agent declares no supported modes") {
            Then("the default tui is still returned") {
                AgentModePolicy.normalizeModeForProfile(
                    "gui",
                    FixedModesProfile(supportedModes = emptyList())
                ) shouldBe "tui"
            }
        }
    }

    Given("an agent identifier that has to be turned into a profile") {

        When("the identifier matches nothing in the list") {
            Then("the default OpenCode profile is chosen") {
                AgentModePolicy.resolveProfile(
                    agentId = "missing-agent",
                    profiles = listOf(ClaudeCodeProfile(), OpenCodeProfile())
                ).id shouldBe "opencode"
            }
        }

        When("the identifier is blank") {
            Then("the default OpenCode profile is chosen") {
                AgentModePolicy.resolveProfile(
                    agentId = " ",
                    profiles = listOf(ClaudeCodeProfile(), OpenCodeProfile())
                ).id shouldBe "opencode"
            }
        }
    }
})
