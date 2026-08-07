package com.agentellij.core.bridge

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class AgentNotificationEventSpec : BehaviorSpec({

    Given("an agent lifecycle bridge route") {

        When("the completion route is decoded") {
            Then("it supplies completion-specific notification copy") {
                val event = AgentNotificationEvent.fromRoute("agent.turnCompleted")!!
                event shouldBe AgentNotificationEvent.TURN_COMPLETED
                event.titleSuffix shouldBe "response completed"
                event.message shouldBe "The agent is ready for your next message."
            }
        }

        When("the structured-question route is decoded") {
            Then("it supplies input-specific notification copy") {
                val event = AgentNotificationEvent.fromRoute("agent.inputRequested")!!
                event shouldBe AgentNotificationEvent.INPUT_REQUESTED
                event.titleSuffix shouldBe "needs your input"
                event.message shouldBe "The agent is waiting for your answer."
            }
        }

        When("an unrelated route is decoded") {
            Then("it is not treated as an agent notification") {
                AgentNotificationEvent.fromRoute("openFile").shouldBeNull()
            }
        }
    }
})
