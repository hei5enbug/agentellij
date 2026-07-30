package com.agentellij.core.settings

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class AgentPathsSpec : BehaviorSpec({

    Given("binary paths remembered per agent") {

        When("a path is stored for each agent") {
            val paths = AgentPaths()
                .withPath("opencode", "/bin/opencode")
                .withPath("claude", "/bin/claude")
                .withPath("codex", "/bin/codex")

            Then("each agent reads back its own path") {
                paths.pathFor("opencode") shouldBe "/bin/opencode"
                paths.pathFor("claude") shouldBe "/bin/claude"
                paths.pathFor("codex") shouldBe "/bin/codex"
            }
        }

        When("an unrecognised agent id is used") {
            Then("it shares the same slot as OpenCode, which is how older settings were stored") {
                AgentPaths().withPath("opencode", "/bin/opencode").pathFor("something-new") shouldBe "/bin/opencode"
            }
        }

        When("nothing has been stored yet") {
            Then("every agent reads back empty") {
                AgentPaths().pathFor("opencode") shouldBe ""
                AgentPaths().pathFor("claude") shouldBe ""
                AgentPaths().pathFor("codex") shouldBe ""
            }
        }
    }

    Given("the Terminal agent, which runs the IDE shell and has no binary") {

        When("its path is read") {
            Then("it is always empty") {
                AgentPaths(shared = "/bin/opencode").pathFor("terminal") shouldBe ""
            }
        }

        When("a path is written for it") {
            val paths = AgentPaths(shared = "/bin/opencode").withPath("terminal", "/bin/ignored")

            Then("the write is ignored") {
                paths.pathFor("terminal") shouldBe ""
            }

            Then("no other agent path is disturbed") {
                paths.pathFor("opencode") shouldBe "/bin/opencode"
            }
        }
    }

    Given("an existing set of paths") {

        When("one path is changed") {
            val original = AgentPaths(shared = "/a", claude = "/b", codex = "/c")
            val updated = original.withPath("claude", "/changed")

            Then("a new set is produced") {
                updated.claude shouldBe "/changed"
            }

            Then("the original is left as it was") {
                original.claude shouldBe "/b"
            }
        }
    }
})
