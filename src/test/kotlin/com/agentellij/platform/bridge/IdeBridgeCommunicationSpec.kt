package com.agentellij.platform.bridge

import com.fasterxml.jackson.databind.JsonNode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private data class ReceivedMessage(val type: String?, val id: String?, val payload: JsonNode?)

/**
 * Exercises the real HTTP surface of the bridge.
 *
 * The pure specs cover the decisions; this covers the wiring around them, which is
 * where an authentication mistake or a broken event stream would actually show up. A
 * recording route handler stands in for the IDE-facing half, so no project is needed.
 */
class IdeBridgeCommunicationSpec : BehaviorSpec({

    val received = mutableListOf<ReceivedMessage>()
    val delivered = CountDownLatch(1)

    // The bridge is a singleton, so the handler this spec installs has to be put back.
    lateinit var originalHandler: BridgeRouteHandler

    beforeSpec {
        originalHandler = IdeBridge.useRouteHandler { session, _, type, id, payload ->
            received += ReceivedMessage(type, id, payload)
            delivered.countDown()
            if (id != null) IdeBridge.replyOk(session, id)
        }
        IdeBridge.start()
    }

    afterSpec {
        IdeBridge.stop()
        IdeBridge.useRouteHandler(originalHandler)
    }

    fun url(path: String) = URI("http://127.0.0.1:${IdeBridge.getPort()}$path").toURL()

    fun get(path: String): Int = (url(path).openConnection() as HttpURLConnection).run {
        requestMethod = "GET"
        connectTimeout = 5000
        readTimeout = 5000
        val code = responseCode
        disconnect()
        code
    }

    fun request(path: String, method: String, body: String? = null): Int =
        (url(path).openConnection() as HttpURLConnection).run {
            requestMethod = method
            connectTimeout = 5000
            readTimeout = 5000
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray()) }
            }
            val code = responseCode
            disconnect()
            code
        }

    Given("a request that does not belong to any session") {

        When("no such session exists") {
            Then("it is refused") {
                get("/idebridge/no-such-session/events?token=whatever") shouldBe 401
            }
        }

        When("the session exists but the token is wrong") {
            val session = IdeBridge.openSession(null)

            Then("it is refused") {
                get("/idebridge/${session.sessionId}/events?token=guess") shouldBe 401
            }

            Then("a missing token is refused too") {
                get("/idebridge/${session.sessionId}/events") shouldBe 401
            }
        }

        When("the path does not name a session and an action") {
            Then("it is not found") {
                get("/idebridge/only-one-segment") shouldBe 404
            }
        }
    }

    Given("a session that has been opened") {

        When("an unknown action is requested") {
            val session = IdeBridge.openSession(null)

            Then("it is not found") {
                get("/idebridge/${session.sessionId}/wat?token=${session.token}") shouldBe 404
            }
        }

        When("a message is sent with the wrong method") {
            val session = IdeBridge.openSession(null)

            Then("the method is refused") {
                request("/idebridge/${session.sessionId}/send?token=${session.token}", "GET") shouldBe 405
            }
        }

        When("a preflight request arrives") {
            val session = IdeBridge.openSession(null)

            Then("it is answered with no content") {
                request("/idebridge/${session.sessionId}/events?token=${session.token}", "OPTIONS") shouldBe 204
            }
        }
    }

    Given("a web client subscribed to the event stream") {

        When("it subscribes and then sends a message") {
            val session = IdeBridge.openSession(null)
            val stream = (url("/idebridge/${session.sessionId}/events?token=${session.token}")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }
            val reader = BufferedReader(InputStreamReader(stream.inputStream))
            val greeting = generateSequence { reader.readLine() }.first { it.isNotBlank() }

            val sendCode = request(
                "/idebridge/${session.sessionId}/send?token=${session.token}",
                "POST",
                """{"type":"kv.get","id":"req-1","payload":{"a":1}}"""
            )
            delivered.await(5, TimeUnit.SECONDS)
            // The stream opens with a connected event whose data is empty, so the reply
            // is the next data line rather than the first one.
            val reply = generateSequence { reader.readLine() }.first { it.contains("replyTo") }
            reader.close()
            stream.disconnect()

            Then("the stream opens with the connected event") {
                greeting shouldContain "connected"
            }

            Then("the message is accepted") {
                sendCode shouldBe 204
            }

            Then("the route handler receives the type, the request id and the payload") {
                val message = received.firstOrNull { it.id == "req-1" }
                message.shouldNotBeNull()
                message.type shouldBe "kv.get"
                message.payload!!.get("a").asInt() shouldBe 1
            }

            Then("the reply comes back down the same stream") {
                reply shouldContain "\"replyTo\":\"req-1\""
                reply shouldContain "\"ok\":true"
            }
        }
    }

    Given("a session that is closed") {

        When("its token is used afterwards") {
            val session = IdeBridge.openSession(null)
            IdeBridge.removeSession(session.sessionId)

            Then("it no longer authenticates") {
                get("/idebridge/${session.sessionId}/events?token=${session.token}") shouldBe 401
            }
        }
    }
})
