package com.agentellij.core.bridge

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class AgentCompletionPolicySpec : BehaviorSpec({

    val supported = setOf("opencode", "claude", "codex")

    Given("a completion message naming an agent") {

        When("the identifier belongs to a supported agent") {
            Then("surrounding transport whitespace is removed") {
                AgentCompletionPolicy.supportedAgentId(" codex ", supported) shouldBe "codex"
            }
        }

        When("the identifier is absent or belongs to an arbitrary terminal command") {
            Then("it is refused") {
                AgentCompletionPolicy.supportedAgentId(null, supported).shouldBeNull()
                AgentCompletionPolicy.supportedAgentId("terminal", supported).shouldBeNull()
                AgentCompletionPolicy.supportedAgentId("other-agent", supported).shouldBeNull()
            }
        }
    }

    Given("completion events arriving for one bridge session") {

        When("the first event arrives") {
            Then("it is delivered") {
                AgentCompletionPolicy.shouldDeliver(null, 10_000) shouldBe true
            }
        }

        When("a second adapter reports the same transition inside the duplicate window") {
            Then("it is suppressed") {
                AgentCompletionPolicy.shouldDeliver(10_000, 11_499) shouldBe false
            }
        }

        When("the next genuine turn finishes at the window boundary") {
            Then("it is delivered") {
                AgentCompletionPolicy.shouldDeliver(10_000, 11_500) shouldBe true
            }
        }

        When("the host clock moves backwards") {
            Then("notifications recover instead of staying suppressed indefinitely") {
                AgentCompletionPolicy.shouldDeliver(10_000, 9_000) shouldBe true
            }
        }
    }
})
