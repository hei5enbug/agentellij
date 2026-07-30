package com.agentellij.platform.config

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.instancio.Instancio
import org.instancio.Select.field

/**
 * Instancio fills the fields this spec does not care about, so each scenario only states
 * the one value it is actually about. Values under assertion are always set explicitly,
 * which keeps the spec repeatable despite the random filling.
 */
class AgentellIJSettingsSpec : BehaviorSpec({

    Given("persisted settings being loaded back into the plugin") {

        When("the stored mode is not a mode the plugin knows") {
            val settings = AgentellIJSettings()
            settings.loadState(
                Instancio.of(AgentellIJSettings.State::class.java)
                    .set(field(AgentellIJSettings.State::class.java, "mode"), "invalid")
                    .create()
            )

            Then("it is normalized to the terminal mode") {
                settings.getMode() shouldBe "tui"
            }
        }

        When("the stored active agent is blank") {
            val settings = AgentellIJSettings()
            settings.loadState(
                Instancio.of(AgentellIJSettings.State::class.java)
                    .set(field(AgentellIJSettings.State::class.java, "activeAgent"), "")
                    .create()
            )

            Then("it falls back to OpenCode") {
                settings.getActiveAgent() shouldBe "opencode"
            }
        }
    }

    Given("binary paths stored for several agents") {

        When("a path is written for each agent") {
            val settings = AgentellIJSettings()
            settings.setAgentPath("opencode", "/bin/opencode")
            settings.setAgentPath("claude", "/bin/claude")
            settings.setAgentPath("codex", "/bin/codex")

            Then("each agent reads back its own path") {
                settings.getAgentPath("opencode") shouldBe "/bin/opencode"
                settings.getAgentPath("claude") shouldBe "/bin/claude"
                settings.getAgentPath("codex") shouldBe "/bin/codex"
            }
        }

        When("a path is written for the Terminal agent, which has no binary") {
            val settings = AgentellIJSettings()
            settings.setAgentPath("opencode", "/bin/opencode")
            settings.setAgentPath("terminal", "/bin/should-be-ignored")

            Then("the Terminal agent still reports no path") {
                settings.getAgentPath("terminal") shouldBe ""
            }

            Then("no other agent path is disturbed") {
                settings.getAgentPath("opencode") shouldBe "/bin/opencode"
            }
        }
    }

})
