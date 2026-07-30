package com.agentellij.core.agent

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Pins what each agent record says about itself.
 *
 * These values decide which binary is looked for, which environment variables are
 * honoured, which surface the agent can appear on and where its conversation state
 * lives. Changing one silently changes behaviour for anyone using that agent, so each is
 * written out here rather than read back from the record under test.
 */
class AgentProfileContractSpec : BehaviorSpec({

    Given("the OpenCode agent") {
        val profile = OpenCodeProfile()

        When("its record is read") {
            Then("it is identified and named as users know it") {
                profile.id shouldBe "opencode"
                profile.displayName shouldBe "OpenCode"
            }

            Then("it looks for the opencode binary and honours its own variable") {
                profile.defaultBinary shouldBe "opencode"
                profile.binaryEnvVars shouldBe listOf("OPENCODE_BIN")
            }

            Then("it is the only agent that offers both surfaces") {
                profile.supportedModes shouldBe listOf("tui", "gui")
            }

            Then("it keeps state in its own directory") {
                profile.stateDirectoryName shouldBe "opencode"
            }

            Then("it needs a binary rather than the IDE shell") {
                profile.usesDefaultShell shouldBe false
            }

            Then("it can be installed, and the command is shown before it runs") {
                profile.installCommandLabel shouldBe "npm install -g opencode-ai"
            }
        }

        When("its output is scanned for the address of its web UI") {
            Then("the listening line is recognised") {
                profile.serverUrlPattern.find("server listening on http://localhost:4096")
                    ?.groupValues?.get(1) shouldBe "http://localhost:4096"
            }

            Then("the prefix is optional") {
                profile.serverUrlPattern.find("listening on http://127.0.0.1:80").shouldNotBeNull()
            }

            Then("an unrelated line is not mistaken for it") {
                profile.serverUrlPattern.find("ready").shouldBeNull()
            }
        }
    }

    Given("the Claude Code agent") {
        val profile = ClaudeCodeProfile()

        When("its record is read") {
            Then("it is identified and named as users know it") {
                profile.id shouldBe "claude"
                profile.displayName shouldBe "Claude Code"
            }

            Then("it looks for the claude binary and honours its own variable") {
                profile.defaultBinary shouldBe "claude"
                profile.binaryEnvVars shouldBe listOf("CLAUDE_CODE_BIN")
            }

            Then("it is terminal only") {
                profile.supportedModes shouldBe listOf("tui")
            }

            Then("it keeps no state of its own on disk") {
                profile.stateDirectoryName.shouldBeNull()
            }

            Then("it can be installed") {
                profile.installCommandLabel shouldBe "npm install -g @anthropic-ai/claude-code"
            }

            Then("no output line is ever taken for a web UI address") {
                profile.serverUrlPattern.find("listening on http://localhost:4096").shouldBeNull()
            }
        }
    }

    Given("the Codex CLI agent") {
        val profile = CodexCliProfile()

        When("its record is read") {
            Then("it is identified and named as users know it") {
                profile.id shouldBe "codex"
                profile.displayName shouldBe "Codex CLI"
            }

            Then("it looks for the codex binary and honours its own variable") {
                profile.defaultBinary shouldBe "codex"
                profile.binaryEnvVars shouldBe listOf("CODEX_BIN")
            }

            Then("it is terminal only and keeps no state of its own") {
                profile.supportedModes shouldBe listOf("tui")
                profile.stateDirectoryName.shouldBeNull()
            }

            Then("it can be installed") {
                profile.installCommandLabel shouldBe "npm install -g @openai/codex"
            }
        }
    }

    Given("the Terminal agent, which is the IDE's own shell") {
        val profile = TerminalProfile()

        When("its record is read") {
            Then("it is identified and named as users know it") {
                profile.id shouldBe "terminal"
                profile.displayName shouldBe "Terminal"
            }

            Then("it names no binary and honours no variable") {
                profile.defaultBinary shouldBe ""
                profile.binaryEnvVars shouldBe emptyList()
            }

            Then("it is terminal only and keeps no state") {
                profile.supportedModes shouldBe listOf("tui")
                profile.stateDirectoryName.shouldBeNull()
            }

            Then("it asks for the default shell instead of a launch command") {
                profile.usesDefaultShell shouldBe true
            }

            Then("it cannot be installed, because there is nothing to install") {
                profile.buildInstallCommand(isWindows = false).shouldBeNull()
                profile.buildInstallCommand(isWindows = true).shouldBeNull()
                profile.installCommandLabel.shouldBeNull()
            }
        }
    }
})
