package com.agentellij.core.bridge

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class BridgeRoutesSpec : BehaviorSpec({

    Given("the set of message types the web client may send") {

        When("the route table is read") {
            Then("it is exactly the nine agreed names") {
                BridgeRoutes.ALL shouldBe setOf(
                    "openFile",
                    "openUrl",
                    "reloadPath",
                    "kv.get",
                    "kv.update",
                    "model.get",
                    "model.update",
                    "settings.get",
                    "settings.update"
                )
            }
        }

        When("a message type arrives") {
            Then("a known type is accepted") {
                BridgeRoutes.isKnown("openFile") shouldBe true
            }

            Then("an unknown type is rejected") {
                BridgeRoutes.isKnown("deleteEverything") shouldBe false
            }

            Then("an absent type is rejected") {
                BridgeRoutes.isKnown(null) shouldBe false
            }

            Then("the rejection message names the offending type") {
                BridgeRoutes.unknownTypeMessage("deleteEverything") shouldBe "Unknown type: deleteEverything"
            }
        }
    }
})
