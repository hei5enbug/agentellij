package com.agentellij.platform.bridge

import com.agentellij.core.util.Diagnostics
import com.agentellij.platform.env.IdeLoggerDiagnostics
import com.agentellij.core.bridge.BridgeRequest
import com.agentellij.core.bridge.MessageEnvelope
import com.agentellij.core.bridge.StaticAssets
import com.agentellij.core.util.closeQuietly
import com.agentellij.core.util.runQuietly
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

object IdeBridge {
    private val LOG by lazy { Logger.getInstance(IdeBridge::class.java) }
    private val mapper = jacksonObjectMapper()
    private val diagnostics: Diagnostics by lazy { IdeLoggerDiagnostics(LOG) }

    private var server: HttpServer? = null
    private var port: Int = 0
    internal fun getPort(): Int = port
    private val registry = BridgeSessionRegistry()
    @Volatile private var executor = Executors.newCachedThreadPool()
    private var keepaliveTimer: Timer? = null

    @Volatile
    private var routeHandler: BridgeRouteHandler = MessageHandler(mapper)

    /**
     * Replaces the route handler and returns the one it displaced.
     *
     * Only the bridge integration spec uses this, and it must put the original back:
     * this is a singleton, so a spec that forgets would change how later specs behave.
     */
    internal fun useRouteHandler(handler: BridgeRouteHandler): BridgeRouteHandler {
        val previous = routeHandler
        routeHandler = handler
        return previous
    }

    @Synchronized
    fun start() {
        if (server != null) return

        if (executor.isShutdown) executor = Executors.newCachedThreadPool()

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            executor = this@IdeBridge.executor
            createContext("/${BridgeRequest.CONTEXT_PATH}") { exchange -> handleRequest(exchange) }
            createContext(StaticAssets.URL_PREFIX) { exchange -> handleStaticFile(exchange) }
            start()
        }
        port = server!!.address.port
        LOG.info("IdeBridge server started on port $port")
    }

    @Synchronized
    fun stop() {
        keepaliveTimer?.cancel()
        keepaliveTimer = null
        server?.stop(0)
        server = null
        registry.closeAll()
        runQuietly(diagnostics, "shut down the bridge executor") { executor.shutdownNow() }
    }

    fun createSession(project: Project): SessionInfo = openSession(project)

    internal fun openSession(project: Project?): SessionInfo {
        start()

        val session = registry.open(project)

        if (keepaliveTimer == null) {
            keepaliveTimer = Timer("IdeBridge-Keepalive", true).apply {
                scheduleAtFixedRate(object : TimerTask() {
                    override fun run() { sendKeepaliveToAll() }
                }, 15000, 15000)
            }
        }

        val baseUrl = "http://127.0.0.1:$port/${BridgeRequest.CONTEXT_PATH}/${session.id}"
        return SessionInfo(baseUrl, session.token, session.id)
    }

    fun removeSession(sessionId: String) = registry.close(sessionId)

    fun send(sessionId: String, type: String, payload: Map<String, Any?> = emptyMap()) {
        val session = registry.identity(sessionId) ?: return
        val msg = MessageEnvelope.event(mapper, type, payload, System.currentTimeMillis())
        broadcastSSE(session, mapper.writeValueAsString(msg))
    }

    fun send(project: Project, type: String, payload: Map<String, Any?> = emptyMap()) {
        val sessionId = registry.sessionIdFor(project)
        if (sessionId == null) {
            LOG.warn("No session found for project: ${project.name}")
            return
        }
        send(sessionId, type, payload)
    }

    internal fun replyOk(session: BridgeSession, id: String?) {
        val msg = MessageEnvelope.success(mapper, id, System.currentTimeMillis()) ?: return
        broadcastSSE(session, mapper.writeValueAsString(msg))
    }

    internal fun replyError(session: BridgeSession, id: String?, error: String) {
        val msg = MessageEnvelope.failure(mapper, id, error, System.currentTimeMillis()) ?: return
        broadcastSSE(session, mapper.writeValueAsString(msg))
    }

    internal fun replyWithPayload(session: BridgeSession, id: String?, payload: Any) {
        val msg = MessageEnvelope.payload(mapper, id, payload, System.currentTimeMillis()) ?: return
        broadcastSSE(session, mapper.writeValueAsString(msg))
    }

    private fun sendKeepaliveToAll() {
        registry.all().forEach { session ->
            synchronized(session.sseClients) {
                val toRemove = mutableListOf<HttpExchange>()
                session.sseClients.forEach { client ->
                    try {
                        val writer = OutputStreamWriter(client.responseBody)
                        writer.write(": ping\n\n")
                        writer.flush()
                    } catch (_: Exception) {
                        toRemove.add(client)
                    }
                }
                toRemove.forEach {
                    session.sseClients.remove(it)
                    it.closeQuietly(diagnostics, "a disconnected event stream")
                }
            }
        }
    }

    private fun handleStaticFile(exchange: HttpExchange) {
        if (!CorsHeaders.apply(exchange, "GET, OPTIONS")) return

        if (exchange.requestMethod == "OPTIONS") {
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
            return
        }

        if (exchange.requestMethod != "GET") {
            exchange.sendResponseHeaders(405, -1)
            exchange.close()
            return
        }

        val resourcePath = StaticAssets.resolveResourcePath(exchange.requestURI.path)
        if (resourcePath == null) {
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
            return
        }

        val inputStream = IdeBridge::class.java.getResourceAsStream(resourcePath)
        if (inputStream == null) {
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
            return
        }

        try {
            val bytes = inputStream.use { it.readBytes() }
            exchange.responseHeaders.set("Content-Type", StaticAssets.mimeTypeFor(resourcePath))
            exchange.responseHeaders.set("Cache-Control", "no-cache")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        } catch (e: Exception) {
            LOG.warn("Error serving static file: $resourcePath", e)
            runQuietly(diagnostics, "report a static asset failure") { exchange.sendResponseHeaders(500, -1) }
        } finally {
            exchange.close()
        }
    }

    private fun handleRequest(exchange: HttpExchange) {
        if (!CorsHeaders.apply(exchange, "GET, POST, OPTIONS")) return

        if (exchange.requestMethod == "OPTIONS") {
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
            return
        }

        val target = BridgeRequest.parseTarget(exchange.requestURI.path)
        if (target == null) {
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
            return
        }

        val session = registry.identity(target.sessionId)
        val token = BridgeRequest.tokenOf(exchange.requestURI.rawQuery)

        if (!BridgeRequest.isAuthorized(session?.token, token)) {
            LOG.warn("IdeBridge unauthorized: sessionId=${target.sessionId} action=${target.action}")
            exchange.sendResponseHeaders(401, -1)
            exchange.close()
            return
        }

        when (target.action) {
            BridgeRequest.ACTION_EVENTS -> handleSSE(exchange, session!!)
            BridgeRequest.ACTION_SEND -> handleSend(exchange, session!!)
            else -> {
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            }
        }
    }

    private fun handleSSE(exchange: HttpExchange, session: BridgeSession) {
        exchange.responseHeaders.apply {
            add("Content-Type", "text/event-stream")
            add("Cache-Control", "no-cache, no-transform")
            add("Connection", "keep-alive")
            add("X-Accel-Buffering", "no")
        }
        exchange.sendResponseHeaders(200, 0)

        synchronized(session.sseClients) {
            session.sseClients.add(exchange)
        }

        try {
            val data = mapper.createObjectNode()
            val writer = OutputStreamWriter(exchange.responseBody)
            writer.write("event: connected\ndata: ${mapper.writeValueAsString(data)}\n\n")
            writer.flush()
        } catch (_: Exception) {
            synchronized(session.sseClients) { session.sseClients.remove(exchange) }
            exchange.closeQuietly(diagnostics, "an event stream")
        }
    }

    private fun handleSend(exchange: HttpExchange, session: BridgeSession) {
        val project = registry.projectOf(session.id)
        if (exchange.requestMethod != "POST") {
            exchange.sendResponseHeaders(405, -1)
            exchange.close()
            return
        }

        try {
            val body = exchange.requestBody.bufferedReader().readText()
            val msg = mapper.readTree(body)

            val type = msg.get("type")?.asText()
            val id = msg.get("id")?.asText()
            val payload = msg.get("payload")

            routeHandler.handle(session, project, type, id, payload)

            exchange.sendResponseHeaders(204, -1)
        } catch (e: Exception) {
            LOG.warn("Error handling send", e)
            exchange.sendResponseHeaders(400, -1)
        }
        exchange.close()
    }

    private fun broadcastSSE(session: BridgeSession, json: String) {
        synchronized(session.sseClients) {
            val toRemove = mutableListOf<HttpExchange>()
            session.sseClients.forEach { client ->
                try {
                    val writer = OutputStreamWriter(client.responseBody)
                    writer.write("event: message\ndata: $json\n\n")
                    writer.flush()
                } catch (_: Exception) {
                    toRemove.add(client)
                }
            }
            toRemove.forEach {
                session.sseClients.remove(it)
                it.closeQuietly(diagnostics, "a disconnected event stream")
            }
        }
    }

}
