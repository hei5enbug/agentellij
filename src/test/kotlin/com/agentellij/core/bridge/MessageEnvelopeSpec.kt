package com.agentellij.core.bridge

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class MessageEnvelopeSpec : BehaviorSpec({

    val mapper = jacksonObjectMapper()
    val timestamp = 1_700_000_000_000L

    Given("an event pushed to the web client") {

        When("the envelope is built") {
            val envelope = MessageEnvelope.event(
                mapper,
                type = "insertPaths",
                payload = mapOf("paths" to listOf("/tmp/a.kt")),
                timestamp = timestamp
            )

            Then("it carries the event type") {
                envelope.get("type").asText() shouldBe "insertPaths"
            }

            Then("it carries the payload") {
                envelope.get("payload").get("paths").get(0).asText() shouldBe "/tmp/a.kt"
            }

            Then("it carries the timestamp it was given rather than reading a clock") {
                envelope.get("timestamp").asLong() shouldBe timestamp
            }
        }
    }

    Given("a reply to a request the web client sent") {

        When("the request succeeded") {
            val envelope = MessageEnvelope.success(mapper, id = "req-1", timestamp = timestamp)!!

            Then("it points back at the request") {
                envelope.get("replyTo").asText() shouldBe "req-1"
            }

            Then("it reports success") {
                envelope.get("ok").asBoolean() shouldBe true
            }
        }

        When("the request failed") {
            val envelope = MessageEnvelope.failure(mapper, id = "req-1", error = "Missing path", timestamp = timestamp)!!

            Then("it reports failure") {
                envelope.get("ok").asBoolean() shouldBe false
            }

            Then("it carries the reason") {
                envelope.get("error").asText() shouldBe "Missing path"
            }
        }

        When("the request produced data") {
            val envelope = MessageEnvelope.payload(mapper, id = "req-1", payload = mapOf("a" to 1), timestamp = timestamp)!!

            Then("it reports success and carries the data") {
                envelope.get("ok").asBoolean() shouldBe true
                envelope.get("payload").get("a").asInt() shouldBe 1
            }
        }
    }

    Given("a message the web client sent without a request identifier") {

        When("a reply would be built") {
            Then("no success envelope is produced, so nothing is sent") {
                MessageEnvelope.success(mapper, id = null, timestamp = timestamp).shouldBeNull()
            }

            Then("no failure envelope is produced either") {
                MessageEnvelope.failure(mapper, id = null, error = "boom", timestamp = timestamp).shouldBeNull()
            }

            Then("no payload envelope is produced either") {
                MessageEnvelope.payload(mapper, id = null, payload = mapOf("a" to 1), timestamp = timestamp).shouldBeNull()
            }
        }
    }
})
