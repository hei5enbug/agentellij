package com.agentellij.core.agent

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.io.File

class AgentStateLocationSpec : BehaviorSpec({

    Given("an agent that keeps state files on disk") {

        When("the XDG state variable is set") {
            Then("the state directory sits under it") {
                AgentStateLocation.resolve(
                    profile = OpenCodeProfile(),
                    userHome = "/home/me",
                    xdgStateHome = "/tmp/state"
                ) shouldBe File("/tmp/state", "opencode")
            }
        }

        When("the XDG state variable is not set") {
            Then("the state directory sits under the home directory default") {
                AgentStateLocation.resolve(
                    profile = OpenCodeProfile(),
                    userHome = "/home/me",
                    xdgStateHome = null
                ) shouldBe File("/home/me/.local/state", "opencode")
            }
        }

        When("the XDG state variable is set to an empty string") {
            val resolved = AgentStateLocation.resolve(
                profile = OpenCodeProfile(),
                userHome = "/home/me",
                xdgStateHome = ""
            )

            Then("the empty value is used rather than treated as absent") {
                resolved shouldBe File("", "opencode")
            }

            Then("that resolves to the filesystem root, not the home directory") {
                resolved!!.path shouldBe "${File.separator}opencode"
            }
        }
    }

    Given("an agent that keeps no state on disk") {

        When("its state directory is asked for") {
            Then("Claude Code has none") {
                AgentStateLocation.resolve(ClaudeCodeProfile(), "/home/me", null).shouldBeNull()
            }

            Then("Codex CLI has none") {
                AgentStateLocation.resolve(CodexCliProfile(), "/home/me", null).shouldBeNull()
            }

            Then("the Terminal agent has none") {
                AgentStateLocation.resolve(TerminalProfile(), "/home/me", null).shouldBeNull()
            }
        }
    }
})
