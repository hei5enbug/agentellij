package com.agentellij.core.launch

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class TerminalShellCommandSpec : BehaviorSpec({

    Given("a launch command that has to survive a shell round trip") {

        When("the command is rendered for a posix shell") {
            Then("empty, spaced, quoted and metacharacter arguments are all protected") {
                TerminalShellCommand.renderInner(
                    listOf("opencode", "", "two words", "has'quote", "dollar$", "plain"),
                    isWindows = false
                ) shouldBe "opencode '' 'two words' 'has'\"'\"'quote' 'dollar$' plain"
            }
        }

        When("the command is rendered for a windows shell") {
            Then("empty, spaced and double-quoted arguments are protected and paths stay intact") {
                TerminalShellCommand.renderInner(
                    listOf("opencode", "", "two words", "has\"quote", "C:\\Tools\\opencode"),
                    isWindows = true
                ) shouldBe "opencode \"\" \"two words\" \"has\\\"quote\" C:\\Tools\\opencode"
            }
        }
    }

    Given("a unix host that has to pick an interactive login shell") {

        When("the SHELL variable names an executable file") {
            Then("that shell is used with login and interactive flags") {
                TerminalShellCommand.wrap(
                    command = listOf("opencode", "--model", "gpt 5"),
                    isWindows = false,
                    env = { if (it == "SHELL") "/opt/homebrew/bin/zsh" else null },
                    isExecutable = { it == "/opt/homebrew/bin/zsh" },
                    fileExists = { false }
                ) shouldBe listOf("/opt/homebrew/bin/zsh", "-l", "-i", "-c", "opencode --model 'gpt 5'")
            }
        }

        When("the SHELL variable names something that cannot be executed") {
            Then("the first standard shell that exists is used instead") {
                TerminalShellCommand.wrap(
                    command = listOf("claude"),
                    isWindows = false,
                    env = { if (it == "SHELL") "/missing/shell" else null },
                    isExecutable = { false },
                    fileExists = { it == "/bin/bash" }
                ) shouldBe listOf("/bin/bash", "-l", "-i", "-c", "claude")
            }
        }

        When("no candidate shell exists at all") {
            Then("the fallback is /bin/sh") {
                TerminalShellCommand.wrap(
                    command = listOf("codex"),
                    isWindows = false,
                    env = { null },
                    isExecutable = { false },
                    fileExists = { false }
                ) shouldBe listOf("/bin/sh", "-l", "-i", "-c", "codex")
            }
        }
    }

    Given("a windows host that has to pick a command interpreter") {

        When("ComSpec is set") {
            Then("that interpreter keeps the terminal open after the command") {
                TerminalShellCommand.wrap(
                    command = listOf("opencode", "two words"),
                    isWindows = true,
                    env = { if (it == "ComSpec") "C:\\Windows\\System32\\cmd.exe" else null },
                    isExecutable = { false },
                    fileExists = { false }
                ) shouldBe listOf("C:\\Windows\\System32\\cmd.exe", "/k", "opencode \"two words\"")
            }
        }

        When("ComSpec is missing") {
            Then("the fallback is cmd.exe") {
                TerminalShellCommand.wrap(
                    command = listOf("opencode"),
                    isWindows = true,
                    env = { null },
                    isExecutable = { false },
                    fileExists = { false }
                ) shouldBe listOf("cmd.exe", "/k", "opencode")
            }
        }
    }
})
