package com.agentellij.core.bridge

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class BridgeCorsPolicySpec : BehaviorSpec({

    Given("a cross-origin request carrying an Origin header") {

        When("the origin points at a loopback host") {
            Then("an IPv4 loopback origin is allowed") {
                CorsPolicy.isAllowedOrigin("http://127.0.0.1:3000") shouldBe true
            }

            Then("a localhost origin is allowed") {
                CorsPolicy.isAllowedOrigin("http://localhost:3000") shouldBe true
            }

            Then("https is allowed as well as http") {
                CorsPolicy.isAllowedOrigin("https://localhost:3000") shouldBe true
            }
        }

        When("the origin points somewhere else") {
            Then("a remote host is rejected") {
                CorsPolicy.isAllowedOrigin("https://example.com") shouldBe false
            }

            Then("a non-http scheme is rejected") {
                CorsPolicy.isAllowedOrigin("file://local") shouldBe false
            }

            Then("a malformed value is rejected") {
                CorsPolicy.isAllowedOrigin("not a url") shouldBe false
            }
        }
    }
})

class CorsDecisionSpec : BehaviorSpec({

    Given("the response headers being prepared for a request") {

        When("the request carries no Origin header") {
            val decision = CorsPolicy.decide(null)

            Then("it is allowed, because it is not a cross-origin browser request") {
                decision.allowed shouldBe true
            }

            Then("no allow-origin header is offered") {
                decision.allowOrigin shouldBe null
            }
        }

        When("the request comes from the machine the IDE runs on") {
            val decision = CorsPolicy.decide("http://localhost:3000")

            Then("it is allowed") {
                decision.allowed shouldBe true
            }

            Then("the origin is echoed back") {
                decision.allowOrigin shouldBe "http://localhost:3000"
            }
        }

        When("the request comes from somewhere else") {
            val decision = CorsPolicy.decide("https://example.com")

            Then("it is refused") {
                decision.allowed shouldBe false
            }

            Then("nothing is echoed back") {
                decision.allowOrigin shouldBe null
            }
        }
    }
})
