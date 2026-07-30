package com.agentellij.core.launch

import com.agentellij.core.agent.ClaudeCodeProfile
import com.agentellij.core.agent.CodexCliProfile
import com.agentellij.core.agent.OpenCodeProfile
import com.agentellij.core.agent.TerminalProfile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class TuiLaunchPlannerSpec : BehaviorSpec({

    val profile = OpenCodeProfile()

    Given("a terminal launch plan being prepared for a command-line agent") {

        When("the settings path points at an executable file") {
            val plan = TuiLaunchPlanner.plan(
                profile = profile,
                settingsPath = " /custom/opencode ",
                customArgs = "--model 'gpt 5'",
                agentellijBin = "/env/opencode",
                agentSpecificEnv = { "/agent-env/opencode" },
                discoverBinary = { "/discovered/opencode" },
                canExecute = { it in setOf("/custom/opencode", "/env/opencode", "/agent-env/opencode") }
            )

            Then("the agent counts as installed") {
                plan.installed shouldBe true
            }

            Then("the settings path wins and the custom arguments are appended") {
                plan.command shouldBe listOf("/custom/opencode", "--model", "gpt 5")
            }
        }

        When("only the plugin environment variable is usable") {
            val plan = TuiLaunchPlanner.plan(
                profile = profile,
                settingsPath = "",
                customArgs = "",
                agentellijBin = " /env/opencode ",
                agentSpecificEnv = { "/agent-env/opencode" },
                discoverBinary = { "/discovered/opencode" },
                canExecute = { it in setOf("/env/opencode", "/agent-env/opencode") }
            )

            Then("it wins over the agent environment variable and discovery") {
                plan.installed shouldBe true
                plan.command shouldBe listOf("/env/opencode")
            }
        }

        When("only the agent environment variable is usable") {
            val plan = TuiLaunchPlanner.plan(
                profile = CodexCliProfile(),
                settingsPath = "",
                customArgs = "--approval-mode full-auto",
                agentellijBin = null,
                agentSpecificEnv = { if (it == "CODEX_BIN") " /agent-env/codex " else null },
                discoverBinary = { "/discovered/codex" },
                canExecute = { it == "/agent-env/codex" }
            )

            Then("it is used before discovery") {
                plan.installed shouldBe true
                plan.command shouldBe listOf("/agent-env/codex", "--approval-mode", "full-auto")
            }
        }

        When("only discovery finds the binary") {
            val plan = TuiLaunchPlanner.plan(
                profile = profile,
                settingsPath = "",
                customArgs = "--flag",
                agentellijBin = null,
                agentSpecificEnv = { null },
                discoverBinary = { if (it == "opencode") "/Users/me/.opencode/bin/opencode" else null },
                canExecute = { false }
            )

            Then("the agent counts as installed") {
                plan.installed shouldBe true
            }

            Then("the command still uses the bare default binary name") {
                plan.command shouldBe listOf("opencode", "--flag")
            }
        }

        When("nothing finds the binary") {
            val plan = TuiLaunchPlanner.plan(
                profile = ClaudeCodeProfile(),
                settingsPath = "/configured/missing-claude",
                customArgs = "--dangerously-skip-permissions",
                agentellijBin = null,
                agentSpecificEnv = { null },
                discoverBinary = { null },
                canExecute = { false }
            )

            Then("the agent is reported as not installed") {
                plan.installed shouldBe false
            }

            Then("the default command is still produced for the install prompt") {
                plan.command shouldBe listOf("claude", "--dangerously-skip-permissions")
            }
        }

        When("a terminal plan is built for an agent that also supports gui") {
            Then("the serve subcommand never leaks into it") {
                TuiLaunchPlanner.plan(
                    profile = profile,
                    settingsPath = "/custom/opencode",
                    customArgs = "",
                    agentellijBin = null,
                    agentSpecificEnv = { null },
                    discoverBinary = { null },
                    canExecute = { it == "/custom/opencode" }
                ).command shouldBe listOf("/custom/opencode")
            }
        }
    }

    Given("the Terminal agent, which has no binary of its own") {

        When("a launch plan is requested") {
            val plan = TuiLaunchPlanner.plan(
                profile = TerminalProfile(),
                settingsPath = "/should/not/matter",
                customArgs = "--ignored",
                agentellijBin = "/env/ignored",
                agentSpecificEnv = { error("agentSpecificEnv must not be queried for a default-shell profile") },
                discoverBinary = { error("discoverBinary must not be called for a default-shell profile") },
                canExecute = { error("canExecute must not be called for a default-shell profile") }
            )

            Then("no binary lookup happens at all") {
                plan.installed shouldBe true
            }

            Then("the plan asks for the default shell with an empty command") {
                plan.usesDefaultShell shouldBe true
                plan.command shouldBe emptyList()
            }
        }
    }
})
