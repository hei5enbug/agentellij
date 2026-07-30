package com.agentellij.core.bridge

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class BridgeRequestSpec : BehaviorSpec({

    Given("the path of an incoming bridge request") {

        When("the path names a session and an action") {
            Then("both are extracted") {
                BridgeRequest.parseTarget("/idebridge/session-1/events") shouldBe
                    BridgeRequestTarget(sessionId = "session-1", action = "events")
            }

            Then("extra trailing segments do not confuse it") {
                BridgeRequest.parseTarget("/idebridge/session-1/send/extra") shouldBe
                    BridgeRequestTarget(sessionId = "session-1", action = "send")
            }
        }

        When("the path is not addressed to the bridge") {
            Then("a different context path is refused") {
                BridgeRequest.parseTarget("/other/session-1/events").shouldBeNull()
            }
        }

        When("the path is too short to address an action") {
            Then("a missing action is refused") {
                BridgeRequest.parseTarget("/idebridge/session-1").shouldBeNull()
            }

            Then("a bare context path is refused") {
                BridgeRequest.parseTarget("/idebridge").shouldBeNull()
            }

            Then("an empty path is refused") {
                BridgeRequest.parseTarget("/").shouldBeNull()
            }
        }
    }

    Given("the query string of an incoming bridge request") {

        When("the query carries parameters") {
            Then("each key maps to its value") {
                BridgeRequest.parseQuery("token=abc&mode=gui") shouldBe
                    mapOf("token" to "abc", "mode" to "gui")
            }

            Then("percent-encoded values are decoded") {
                BridgeRequest.parseQuery("token=a%20b") shouldBe mapOf("token" to "a b")
            }

            Then("a key without a value becomes an empty string") {
                BridgeRequest.parseQuery("token=") shouldBe mapOf("token" to "")
            }

            Then("a value containing an equals sign is kept whole") {
                BridgeRequest.parseQuery("token=a=b") shouldBe mapOf("token" to "a=b")
            }
        }

        When("the query is empty or absent") {
            Then("an empty query yields nothing") {
                BridgeRequest.parseQuery("") shouldBe emptyMap()
            }

            Then("an absent query yields no token") {
                BridgeRequest.tokenOf(null).shouldBeNull()
            }
        }

        When("the token is read out") {
            Then("it is taken from the token parameter") {
                BridgeRequest.tokenOf("token=secret&other=1") shouldBe "secret"
            }

            Then("a query without a token parameter yields nothing") {
                BridgeRequest.tokenOf("other=1").shouldBeNull()
            }
        }
    }

    Given("a request that has to prove it belongs to a session") {

        When("the presented token matches the session") {
            Then("the request is authorized") {
                BridgeRequest.isAuthorized(sessionToken = "secret", presentedToken = "secret") shouldBe true
            }
        }

        When("the token does not match") {
            Then("a wrong token is refused") {
                BridgeRequest.isAuthorized(sessionToken = "secret", presentedToken = "guess") shouldBe false
            }

            Then("a missing token is refused") {
                BridgeRequest.isAuthorized(sessionToken = "secret", presentedToken = null) shouldBe false
            }
        }

        When("the session does not exist") {
            Then("the request is refused even if a token was presented") {
                BridgeRequest.isAuthorized(sessionToken = null, presentedToken = "secret") shouldBe false
            }

            Then("two absent tokens do not accidentally match") {
                BridgeRequest.isAuthorized(sessionToken = null, presentedToken = null) shouldBe false
            }
        }
    }
})
