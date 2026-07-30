package com.agentellij.core.bridge

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.net.URI

class BridgeUiUrlSpec : BehaviorSpec({

    val url = BridgeUiUrl.build(
        bridgeBaseUrl = "http://127.0.0.1:54321/idebridge/session-1",
        token = "tok en",
        agentApiUrl = "http://localhost:4096",
        agentName = "Claude Code",
        pluginVersion = "0.4.3"
    )

    Given("the address handed to the embedded browser") {

        When("the address is built") {
            Then("it points at the index page on the bridge origin") {
                url shouldStartWith "http://127.0.0.1:54321/ui/index.html?"
            }

            Then("it carries the five parameters the web client reads") {
                val query = URI(url).query.split("&").map { it.substringBefore("=") }
                query shouldBe listOf("opencodeApi", "ideBridge", "ideBridgeToken", "agentName", "v")
            }
        }

        When("a value contains characters that need escaping") {
            Then("the token is percent-encoded") {
                url shouldContain "ideBridgeToken=tok+en"
            }

            Then("the agent name is percent-encoded") {
                url shouldContain "agentName=Claude+Code"
            }

            Then("the agent api address is percent-encoded") {
                url shouldContain "opencodeApi=http%3A%2F%2Flocalhost%3A4096"
            }
        }

        When("the session part of the bridge address is examined") {
            Then("the origin is taken but the full bridge address is still passed through") {
                url shouldContain "ideBridge=http%3A%2F%2F127.0.0.1%3A54321%2Fidebridge%2Fsession-1"
            }
        }
    }
})
