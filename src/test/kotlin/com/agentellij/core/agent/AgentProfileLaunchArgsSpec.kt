package com.agentellij.core.agent

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class AgentProfileLaunchArgsSpec : BehaviorSpec({

    Given("an agent profile building its launch arguments") {

        When("OpenCode is launched") {
            Then("gui mode inserts the serve subcommand") {
                OpenCodeProfile().buildLaunchArgs("opencode", "--flag value", "gui") shouldBe
                    listOf("opencode", "serve", "--flag", "value")
            }

            Then("tui mode leaves the serve subcommand out") {
                OpenCodeProfile().buildLaunchArgs("opencode", "--flag", "tui") shouldBe
                    listOf("opencode", "--flag")
            }
        }

        When("a terminal-only agent is asked for gui arguments") {
            Then("Claude Code never gains a serve subcommand") {
                ClaudeCodeProfile().buildLaunchArgs("claude", "--dangerously-skip-permissions", "gui") shouldBe
                    listOf("claude", "--dangerously-skip-permissions")
            }

            Then("Codex CLI never gains a serve subcommand") {
                CodexCliProfile().buildLaunchArgs("codex", "--model gpt-5", "gui") shouldBe
                    listOf("codex", "--model", "gpt-5")
            }
        }

        When("the Terminal profile is used") {
            Then("it produces no launch arguments at all") {
                TerminalProfile().buildLaunchArgs("", "", "tui") shouldBe emptyList()
            }
        }
    }

    Given("custom arguments written with shell syntax") {

        When("the arguments are appended to a launch command") {
            Then("a double-quoted path keeps its spaces") {
                OpenCodeProfile()
                    .buildLaunchArgs("opencode", "--config \"/Users/me/My Config/opencode.json\"", "tui") shouldBe
                    listOf("opencode", "--config", "/Users/me/My Config/opencode.json")
            }

            Then("single quotes and escaped spaces are both honoured") {
                ClaudeCodeProfile()
                    .buildLaunchArgs("claude", "--name 'Claude Code' --path /tmp/my\\ file", "tui") shouldBe
                    listOf("claude", "--name", "Claude Code", "--path", "/tmp/my file")
            }

            Then("runs of whitespace collapse into a single separator") {
                OpenCodeProfile().buildLaunchArgs("opencode", "   --one    --two\tvalue   ", "tui") shouldBe
                    listOf("opencode", "--one", "--two", "value")
            }

            Then("an empty quoted pair becomes an empty argument") {
                ClaudeCodeProfile().buildLaunchArgs("claude", "--name \"\" --fallback ''", "tui") shouldBe
                    listOf("claude", "--name", "", "--fallback", "")
            }
        }
    }

    Given("an agent that can be installed from npm") {

        When("the install command is built for a unix host") {
            Then("OpenCode installs the opencode-ai package") {
                OpenCodeProfile().buildInstallCommand(isWindows = false) shouldBe
                    listOf("npm", "install", "-g", "opencode-ai")
            }

            Then("Claude Code installs the anthropic package") {
                ClaudeCodeProfile().buildInstallCommand(isWindows = false) shouldBe
                    listOf("npm", "install", "-g", "@anthropic-ai/claude-code")
            }

            Then("Codex CLI installs the openai package") {
                CodexCliProfile().buildInstallCommand(isWindows = false) shouldBe
                    listOf("npm", "install", "-g", "@openai/codex")
            }
        }

        When("the install command is built for a windows host") {
            Then("the command is wrapped in a cmd invocation") {
                CodexCliProfile().buildInstallCommand(isWindows = true) shouldBe
                    listOf("cmd", "/c", "npm", "install", "-g", "@openai/codex")
            }
        }
    }
})
