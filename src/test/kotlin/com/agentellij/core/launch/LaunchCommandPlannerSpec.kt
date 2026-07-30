package com.agentellij.core.launch

import com.agentellij.core.agent.ClaudeCodeProfile
import com.agentellij.core.agent.OpenCodeProfile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class LaunchCommandPlannerSpec : BehaviorSpec({

    Given("an agent being started with custom arguments") {

        When("the launch is planned") {
            val attempts = LaunchCommandPlanner.attempts(
                profile = OpenCodeProfile(),
                mode = "gui",
                binary = "/bin/opencode",
                customArgs = "--model gpt-5"
            )

            Then("there are two attempts, so a bad argument can be recovered from") {
                attempts shouldHaveSize 2
            }

            Then("the first attempt carries the custom arguments") {
                attempts[0] shouldBe listOf("/bin/opencode", "serve", "--model", "gpt-5")
            }

            Then("the second attempt drops them") {
                attempts[1] shouldBe listOf("/bin/opencode", "serve")
            }
        }

        When("the arguments are only whitespace") {
            Then("they count as absent, so there is nothing to retry without") {
                LaunchCommandPlanner.attempts(
                    profile = OpenCodeProfile(),
                    mode = "gui",
                    binary = "/bin/opencode",
                    customArgs = "   "
                ) shouldHaveSize 1
            }
        }
    }

    Given("an agent being started with no custom arguments") {

        When("the launch is planned") {
            val attempts = LaunchCommandPlanner.attempts(
                profile = ClaudeCodeProfile(),
                mode = "tui",
                binary = "/bin/claude",
                customArgs = ""
            )

            Then("there is exactly one attempt") {
                attempts shouldHaveSize 1
            }

            Then("a second attempt would be identical, so the agent is not started twice") {
                attempts.single() shouldBe listOf("/bin/claude")
            }
        }
    }
})
