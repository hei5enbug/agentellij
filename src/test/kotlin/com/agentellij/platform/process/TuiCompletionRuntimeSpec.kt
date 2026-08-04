package com.agentellij.platform.process

import com.agentellij.core.agent.AgentCompletionHooks
import com.agentellij.platform.bridge.BridgeRouteHandler
import com.agentellij.platform.bridge.IdeBridge
import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.project.Project
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import java.io.File
import java.lang.reflect.Proxy
import java.net.HttpURLConnection
import java.net.URI

private fun completionRuntimeProject(): Project = Proxy.newProxyInstance(
    Project::class.java.classLoader,
    arrayOf(Project::class.java)
) { proxy, method, args ->
    when (method.name) {
        "equals" -> proxy === args?.firstOrNull()
        "hashCode" -> System.identityHashCode(proxy)
        "toString" -> "completion-runtime-project"
        else -> null
    }
} as Project

class TuiCompletionRuntimeSpec : BehaviorSpec({
    data class Received(val type: String?, val payload: JsonNode?)

    val received = mutableListOf<Received>()
    lateinit var originalHandler: BridgeRouteHandler

    beforeSpec {
        originalHandler = IdeBridge.useRouteHandler { _, _, type, _, payload ->
            received += Received(type, payload)
        }
    }

    afterSpec {
        IdeBridge.stop()
        IdeBridge.useRouteHandler(originalHandler)
    }

    fun post(url: String, body: String): Int = (URI(url).toURL().openConnection() as HttpURLConnection).run {
        requestMethod = "POST"
        connectTimeout = 5_000
        readTimeout = 5_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        outputStream.use { it.write(body.toByteArray()) }
        val code = responseCode
        disconnect()
        code
    }

    Given("a POSIX terminal surface with all supported agents installed") {
        val adapterDirectory = tempdir().toPath()
        val runtime = TuiCompletionRuntime.create(
            project = completionRuntimeProject(),
            agentBinaries = mapOf(
                "codex" to "/opt/agents/codex",
                "claude" to "/opt/agents/claude",
                "opencode" to "/opt/agents/opencode"
            ),
            inheritedPath = "/usr/local/bin:/usr/bin",
            inheritedOpenCodeConfig = """{"plugin":["existing"]}""",
            adapterDirectory = adapterDirectory,
            isWindows = false
        )

        When("the runtime is created") {
            Then("only its terminal environment receives the authenticated callback") {
                runtime.environment[AgentCompletionHooks.NOTIFY_URL_ENV]
                    .shouldNotBeNull()
                    .shouldStartWith("http://127.0.0.1:")
                runtime.environment[AgentCompletionHooks.CODEX_BINARY_ENV] shouldBe "/opt/agents/codex"
                runtime.environment[AgentCompletionHooks.CLAUDE_BINARY_ENV] shouldBe "/opt/agents/claude"
                runtime.environment[AgentCompletionHooks.OPENCODE_BINARY_ENV] shouldBe "/opt/agents/opencode"
            }

            Then("its wrappers precede the inherited search path without removing it") {
                runtime.environment["PATH"] shouldBe
                    "$adapterDirectory${File.pathSeparator}/usr/local/bin:/usr/bin"
                runtime.shellActivationCommand shouldBe
                    "export PATH='$adapterDirectory':\"${'$'}PATH\""
            }

            Then("the fixed-size adapter set is materialized") {
                listOf(
                    "notify-agentellij",
                    "codex",
                    "claude",
                    "opencode",
                    "claude-settings.json",
                    "opencode-agentellij.js"
                ).forEach { name -> adapterDirectory.resolve(name).toFile().exists() shouldBe true }
            }

            Then("stable adapter files contain no session secret or resolved agent binary") {
                val callback = runtime.environment[AgentCompletionHooks.NOTIFY_URL_ENV]!!
                adapterDirectory.toFile().walkTopDown().filter { it.isFile }.forEach { adapter ->
                    val content = adapter.readText()
                    content shouldNotContain callback
                    content shouldNotContain "/opt/agents/"
                }
            }

            Then("OpenCode keeps the user's plugin and gains the session adapter") {
                val inlineConfig = runtime.environment[AgentCompletionHooks.OPENCODE_INLINE_CONFIG_ENV]
                    .shouldNotBeNull()
                val plugins = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                    .readTree(inlineConfig)["plugin"].map { it.asText() }
                plugins.shouldContain("existing")
                plugins.any { it.endsWith("/opencode-agentellij.js") } shouldBe true
            }

            Then("direct AgentellIJ launches are routed through the matching wrapper") {
                runtime.wrapCommand("codex", listOf("/opt/agents/codex", "--model", "x")) shouldBe
                    listOf(adapterDirectory.resolve("codex").toString(), "--model", "x")
                runtime.wrapCommand("opencode", listOf("/opt/agents/opencode")) shouldBe
                    listOf(adapterDirectory.resolve("opencode").toString())
            }
        }

        When("an adapter posts a completion message") {
            val code = post(
                runtime.environment[AgentCompletionHooks.NOTIFY_URL_ENV]!!,
                """{"type":"agent.turnCompleted","payload":{"agentId":"codex"}}"""
            )

            Then("the authenticated bridge accepts and routes it") {
                code shouldBe 204
                received.last().type shouldBe "agent.turnCompleted"
                received.last().payload?.get("agentId")?.asText() shouldBe "codex"
            }
        }

        When("the terminal mode is disposed") {
            val callback = runtime.environment[AgentCompletionHooks.NOTIFY_URL_ENV]!!
            runtime.dispose()

            Then("the same callback token no longer authenticates") {
                post(
                    callback,
                    """{"type":"agent.turnCompleted","payload":{"agentId":"codex"}}"""
                ) shouldBe 401
            }
        }
    }

    Given("a terminal whose inherited OpenCode inline configuration is malformed") {
        val adapterDirectory = tempdir().toPath()
        val runtime = TuiCompletionRuntime.create(
            project = completionRuntimeProject(),
            agentBinaries = mapOf("opencode" to "/opt/agents/opencode"),
            inheritedPath = "/usr/bin",
            inheritedOpenCodeConfig = "{not-json",
            adapterDirectory = adapterDirectory,
            isWindows = false
        )

        When("the runtime cannot safely merge the completion plugin") {
            Then("it does not replace the user's value or intercept OpenCode") {
                runtime.environment[AgentCompletionHooks.OPENCODE_INLINE_CONFIG_ENV] shouldBe null
                runtime.environment[AgentCompletionHooks.OPENCODE_RUNTIME_CONFIG_ENV] shouldBe null
                runtime.wrapCommand("opencode", listOf("/opt/agents/opencode")) shouldBe
                    listOf("/opt/agents/opencode")
            }
        }

        runtime.dispose()
    }
})
