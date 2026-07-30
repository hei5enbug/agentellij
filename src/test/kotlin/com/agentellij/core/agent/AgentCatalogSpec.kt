package com.agentellij.core.agent

import com.agentellij.core.settings.AgentModePolicy
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class AgentSwitchUiContractSpec : BehaviorSpec({

    val profiles = AgentCatalog.allProfiles()

    Given("the agent selector reading the supported profiles") {

        When("the dropdown is populated") {
            Then("the identifiers appear in the registered order") {
                profiles.map { it.id } shouldBe listOf("opencode", "claude", "codex", "terminal")
            }

            Then("the display names match what the user sees") {
                profiles.map { it.displayName } shouldBe
                    listOf("OpenCode", "Claude Code", "Codex CLI", "Terminal")
            }
        }

        When("the mode toggle asks each agent whether it offers a choice") {
            Then("only OpenCode does") {
                profiles.associate { it.id to AgentModePolicy.offersModeChoice(it) } shouldBe mapOf(
                    "opencode" to true,
                    "claude" to false,
                    "codex" to false,
                    "terminal" to false
                )
            }
        }
    }
})
