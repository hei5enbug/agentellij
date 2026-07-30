package com.agentellij.core.settings

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class AgentPathEditStateSpec : BehaviorSpec({

    fun initial() = AgentPathEditState.initial(
        agentIds = listOf("opencode", "claude", "codex"),
        selectedAgentId = "opencode",
        pathProvider = { agentId -> "/bin/$agentId" }
    )

    Given("the settings panel opened on the OpenCode agent") {

        When("nothing has been edited yet") {
            Then("the stored path for the selected agent is shown") {
                initial().currentPath() shouldBe "/bin/opencode"
            }
        }

        When("the user edits the path and switches agent") {
            val result = initial().selectAgent(agentId = "codex", currentPath = "/custom/opencode")

            Then("the path of the agent being switched to is shown") {
                result.pathToShow shouldBe "/bin/codex"
            }

            Then("the edit that was in the field is remembered") {
                result.state.snapshot("codex", "/bin/codex").paths["opencode"] shouldBe "/custom/opencode"
            }
        }

        When("the user edits and switches twice, then saves") {
            val afterFirst = initial().selectAgent(agentId = "codex", currentPath = "/custom/opencode")
            val afterSecond = afterFirst.state.selectAgent(agentId = "claude", currentPath = "/custom/codex")
            val snapshot = afterSecond.state.snapshot(selectedAgentId = "claude", currentPath = "/custom/claude")

            Then("each switch shows the stored path of its agent") {
                afterFirst.pathToShow shouldBe "/bin/codex"
                afterSecond.pathToShow shouldBe "/bin/claude"
            }

            Then("every edit made along the way reaches the saved snapshot") {
                snapshot.paths["opencode"] shouldBe "/custom/opencode"
                snapshot.paths["codex"] shouldBe "/custom/codex"
                snapshot.paths["claude"] shouldBe "/custom/claude"
            }
        }

        When("the user returns to an agent they already edited") {
            val afterFirst = initial().selectAgent(agentId = "codex", currentPath = "/custom/opencode")
            val backAgain = afterFirst.state.selectAgent(agentId = "opencode", currentPath = "/custom/codex")

            Then("the earlier edit comes back rather than the stored value") {
                backAgain.pathToShow shouldBe "/custom/opencode"
            }
        }

        When("an agent that was never listed is selected") {
            Then("it starts out empty") {
                initial().selectAgent(agentId = "terminal", currentPath = "/custom/opencode").pathToShow shouldBe ""
            }
        }
    }

    Given("an edit state being used") {

        When("an operation produces a new state") {
            val original = initial()
            original.selectAgent(agentId = "codex", currentPath = "/custom/opencode")

            Then("the state it was called on is left untouched") {
                original.currentPath() shouldBe "/bin/opencode"
                original.snapshot("opencode", "/bin/opencode").paths["opencode"] shouldBe "/bin/opencode"
            }
        }
    }
})
