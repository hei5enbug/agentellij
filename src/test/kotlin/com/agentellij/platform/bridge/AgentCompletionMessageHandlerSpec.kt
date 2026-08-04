package com.agentellij.platform.bridge

import com.agentellij.core.bridge.BridgeRoutes
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.project.Project
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.lang.reflect.Proxy
import java.util.Collections

private fun completionHandlerProject(): Project = Proxy.newProxyInstance(
    Project::class.java.classLoader,
    arrayOf(Project::class.java)
) { proxy, method, args ->
    when (method.name) {
        "isDisposed" -> false
        "equals" -> proxy === args?.firstOrNull()
        "hashCode" -> System.identityHashCode(proxy)
        "toString" -> "completion-handler-project"
        else -> null
    }
} as Project

class AgentCompletionMessageHandlerSpec : BehaviorSpec({
    val mapper = jacksonObjectMapper()

    fun session() = BridgeSession(
        id = "session",
        token = "token",
        sseClients = Collections.synchronizedSet(mutableSetOf()),
        lastCompletionAt = Collections.synchronizedMap(mutableMapOf())
    )

    Given("an authenticated completion message from a supported agent") {
        val delivered = mutableListOf<String>()
        val handler = MessageHandler(
            mapper = mapper,
            nowMillis = { 10_000L },
            completionNotifier = { _, displayName -> delivered += displayName }
        )
        val bridgeSession = session()
        val project = completionHandlerProject()
        val payload = mapper.createObjectNode().put("agentId", "codex")

        When("the message is handled") {
            handler.handle(bridgeSession, project, BridgeRoutes.AGENT_TURN_COMPLETED, null, payload)

            Then("the supported profile's display name reaches the IDE notification adapter") {
                delivered.shouldContainExactly("Codex CLI")
            }

            Then("the delivery time is recorded on that bridge session") {
                bridgeSession.lastCompletionAt["codex"] shouldBe 10_000L
            }
        }

        When("the same transition is reported again immediately") {
            handler.handle(bridgeSession, project, BridgeRoutes.AGENT_TURN_COMPLETED, null, payload)

            Then("a second balloon is not produced") {
                delivered.shouldContainExactly("Codex CLI")
            }
        }
    }

    Given("a completion message that does not name a supported AI agent") {
        val delivered = mutableListOf<String>()
        val handler = MessageHandler(
            mapper = mapper,
            completionNotifier = { _, displayName -> delivered += displayName }
        )

        When("the Terminal profile is presented as though it were an agent") {
            handler.handle(
                session(),
                completionHandlerProject(),
                BridgeRoutes.AGENT_TURN_COMPLETED,
                null,
                mapper.createObjectNode().put("agentId", "terminal")
            )

            Then("no notification is delivered") {
                delivered shouldBe emptyList()
            }
        }
    }

    Given("a completion message whose project has already been disposed") {
        val delivered = mutableListOf<String>()
        val disposedProject = Proxy.newProxyInstance(
            Project::class.java.classLoader,
            arrayOf(Project::class.java)
        ) { _, method, _ -> if (method.name == "isDisposed") true else null } as Project
        val handler = MessageHandler(
            mapper = mapper,
            completionNotifier = { _, displayName -> delivered += displayName }
        )

        When("the message is handled") {
            handler.handle(
                session(),
                disposedProject,
                BridgeRoutes.AGENT_TURN_COMPLETED,
                null,
                mapper.createObjectNode().put("agentId", "claude")
            )

            Then("the closed project is never retained by a notification") {
                delivered shouldBe emptyList()
            }
        }
    }
})
