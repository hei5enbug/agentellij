package com.agentellij.core.settings

import com.agentellij.core.agent.ClaudeCodeProfile
import com.agentellij.core.agent.OpenCodeProfile
import com.agentellij.core.agent.TerminalProfile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class SettingsFormPolicySpec : BehaviorSpec({

    val stored = SettingsFormValues(
        agentId = "opencode",
        mode = "tui",
        agentPaths = mapOf("opencode" to "/bin/opencode", "claude" to "", "codex" to "", "terminal" to ""),
        customArgs = "--flag"
    )

    Given("the settings panel deciding whether Apply should light up") {

        When("nothing has been touched") {
            Then("it reports no change") {
                SettingsFormPolicy.isModified(shown = stored, stored = stored) shouldBe false
            }
        }

        When("a single value differs") {
            Then("a different agent counts as a change") {
                SettingsFormPolicy.isModified(stored.copy(agentId = "claude"), stored) shouldBe true
            }

            Then("a different mode counts as a change") {
                SettingsFormPolicy.isModified(stored.copy(mode = "gui"), stored) shouldBe true
            }

            Then("a different path counts as a change") {
                SettingsFormPolicy.isModified(
                    stored.copy(agentPaths = stored.agentPaths + ("codex" to "/bin/codex")),
                    stored
                ) shouldBe true
            }

            Then("different arguments count as a change") {
                SettingsFormPolicy.isModified(stored.copy(customArgs = "--other"), stored) shouldBe true
            }
        }

        When("only surrounding whitespace differs") {
            Then("it still counts as a change, because saving would alter what is stored") {
                SettingsFormPolicy.isModified(stored.copy(customArgs = "--flag "), stored) shouldBe true
            }
        }
    }

    Given("the settings panel being saved") {

        When("paths and arguments carry stray whitespace") {
            val saved = SettingsFormPolicy.normalizeForSave(
                stored.copy(
                    agentPaths = mapOf("opencode" to "  /bin/opencode  "),
                    customArgs = "  --flag  "
                ),
                OpenCodeProfile()
            )

            Then("paths are trimmed") {
                saved.agentPaths["opencode"] shouldBe "/bin/opencode"
            }

            Then("arguments are trimmed") {
                saved.customArgs shouldBe "--flag"
            }
        }

        When("the chosen mode is one the chosen agent cannot provide") {
            Then("the mode is corrected before it is stored") {
                SettingsFormPolicy.normalizeForSave(
                    stored.copy(mode = "gui"),
                    ClaudeCodeProfile()
                ).mode shouldBe "tui"
            }
        }

        When("the chosen mode is one the agent supports") {
            Then("it is stored as chosen") {
                SettingsFormPolicy.normalizeForSave(
                    stored.copy(mode = "gui"),
                    OpenCodeProfile()
                ).mode shouldBe "gui"
            }
        }
    }

    Given("the mode dropdown, which shows sentences rather than mode names") {

        When("a label is read back") {
            Then("the terminal label maps to the terminal mode") {
                SettingsFormPolicy.modeFromLabel(SettingsFormPolicy.TUI_MODE_LABEL) shouldBe "tui"
            }

            Then("the graphical label maps to the graphical mode") {
                SettingsFormPolicy.modeFromLabel(SettingsFormPolicy.GUI_MODE_LABEL) shouldBe "gui"
            }

            Then("an absent selection is treated as the graphical mode, as it always has been") {
                SettingsFormPolicy.modeFromLabel(null) shouldBe "gui"
            }
        }

        When("a mode is displayed") {
            Then("the terminal mode shows the terminal label") {
                SettingsFormPolicy.labelForMode("tui") shouldBe SettingsFormPolicy.TUI_MODE_LABEL
            }

            Then("the graphical mode shows the graphical label") {
                SettingsFormPolicy.labelForMode("gui") shouldBe SettingsFormPolicy.GUI_MODE_LABEL
            }

            Then("an unrecognised mode falls back to the terminal label") {
                SettingsFormPolicy.labelForMode("nonsense") shouldBe SettingsFormPolicy.TUI_MODE_LABEL
            }
        }
    }

    Given("the binary path field") {

        When("an agent that needs a binary is selected") {
            Then("the field is usable") {
                SettingsFormPolicy.pathFieldEnabled(OpenCodeProfile()) shouldBe true
            }
        }

        When("the Terminal agent is selected") {
            Then("the field is locked because there is no binary to point at") {
                SettingsFormPolicy.pathFieldEnabled(TerminalProfile()) shouldBe false
            }
        }
    }
})
