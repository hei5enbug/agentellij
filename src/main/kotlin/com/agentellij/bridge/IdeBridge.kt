package com.agentellij.bridge

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import com.agentellij.util.closeQuietly
import com.agentellij.util.runQuietly

object IdeBridge {
    private val LOG by lazy { Logger.getInstance(IdeBridge::class.java) }
    private val mapper = jacksonObjectMapper()

    private var server: HttpServer? = null
    private var port: Int = 0
    internal fun getPort(): Int = port
    private val sessions = ConcurrentHashMap<String, BridgeSession>()
    private val projectToSession = ConcurrentHashMap<Project, String>()
    @Volatile private var executor = Executors.newCachedThreadPool()
    private var keepaliveTimer: Timer? = null

    private val messageHandler = MessageHandler(mapper)

    @Synchronized
    fun start() {
        if (server != null) return

        if (executor.isShutdown) executor = Executors.newCachedThreadPool()

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            executor = this@IdeBridge.executor
            createContext("/idebridge") { exchange -> handleRequest(exchange) }
            createContext("/ui") { exchange -> handleStaticFile(exchange) }
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
        sessions.clear()
        projectToSession.clear()
        runQuietly { executor.shutdownNow() }
    }

    fun createSession(project: Project): SessionInfo {
        start()

        projectToSession[project]?.let { oldId -> removeSession(oldId) }

        val sessionId = UUID.randomUUID().toString()
        val token = UUID.randomUUID().toString()
        sessions[sessionId] = BridgeSession(sessionId, token, project)
        projectToSession[project] = sessionId

        if (keepaliveTimer == null) {
            keepaliveTimer = Timer("IdeBridge-Keepalive", true).apply {
                scheduleAtFixedRate(object : TimerTask() {
                    override fun run() { sendKeepaliveToAll() }
                }, 15000, 15000)
            }
        }

        val baseUrl = "http://127.0.0.1:$port/idebridge/$sessionId"
        return SessionInfo(baseUrl, token, sessionId)
    }

    fun removeSession(sessionId: String) {
        sessions.remove(sessionId)?.let { session ->
            projectToSession.remove(session.project)
            synchronized(session.sseClients) {
                session.sseClients.forEach { it.closeQuietly() }
            }
        }
    }

    fun send(sessionId: String, type: String, payload: Map<String, Any?> = emptyMap()) {
        val session = sessions[sessionId] ?: return
        val msg = mapper.createObjectNode().apply {
            put("type", type)
            set<com.fasterxml.jackson.databind.JsonNode>("payload", mapper.valueToTree(payload))
            put("timestamp", System.currentTimeMillis())
        }
        broadcastSSE(session, mapper.writeValueAsString(msg))
    }

    fun send(project: Project, type: String, payload: Map<String, Any?> = emptyMap()) {
        val sessionId = projectToSession[project]
        if (sessionId == null) {
            LOG.warn("No session found for project: ${project.name}")
            return
        }
        send(sessionId, type, payload)
    }

    internal fun replyOk(session: BridgeSession, id: String?) {
        if (id == null) return
        val msg = mapper.createObjectNode().apply {
            put("replyTo", id)
            put("ok", true)
            put("timestamp", System.currentTimeMillis())
        }
        broadcastSSE(session, mapper.writeValueAsString(msg))
    }

    internal fun replyError(session: BridgeSession, id: String?, error: String) {
        if (id == null) return
        val msg = mapper.createObjectNode().apply {
            put("replyTo", id)
            put("ok", false)
            put("error", error)
            put("timestamp", System.currentTimeMillis())
        }
        broadcastSSE(session, mapper.writeValueAsString(msg))
    }

    internal fun replyWithPayload(session: BridgeSession, id: String?, payload: Any) {
        if (id == null) return
        val msg = mapper.createObjectNode().apply {
            put("replyTo", id)
            put("ok", true)
            set<com.fasterxml.jackson.databind.JsonNode>("payload", mapper.valueToTree(payload))
            put("timestamp", System.currentTimeMillis())
        }
        broadcastSSE(session, mapper.writeValueAsString(msg))
    }

    private fun sendKeepaliveToAll() {
        sessions.values.forEach { session ->
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
                    it.closeQuietly()
                }
            }
        }
    }

    private fun handleStaticFile(exchange: HttpExchange) {
        exchange.responseHeaders.apply {
            add("Access-Control-Allow-Origin", "*")
            add("Access-Control-Allow-Methods", "GET, OPTIONS")
            add("Access-Control-Allow-Headers", "Content-Type")
        }

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

        val rawPath = exchange.requestURI.path.removePrefix("/ui")
        val path = when {
            rawPath.isEmpty() || rawPath == "/" -> "index.html"
            rawPath.startsWith("/") -> rawPath.substring(1)
            else -> rawPath
        }

        val decoded = URLDecoder.decode(path, "UTF-8")
        if (decoded.contains("..") || decoded.contains("\\")) {
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
            return
        }

        val resourcePath = "/webui/$decoded"
        val inputStream = IdeBridge::class.java.getResourceAsStream(resourcePath)

        if (inputStream == null) {
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
            return
        }

        try {
            val bytes = inputStream.use { it.readBytes() }
            exchange.responseHeaders.set("Content-Type", mimeTypeFor(decoded))
            exchange.responseHeaders.set("Cache-Control", "no-cache")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        } catch (e: Exception) {
            LOG.warn("Error serving static file: $resourcePath", e)
            runQuietly { exchange.sendResponseHeaders(500, -1) }
        } finally {
            exchange.close()
        }
    }

    private fun mimeTypeFor(path: String): String = when {
        path.endsWith(".html") -> "text/html; charset=utf-8"
        path.endsWith(".css") -> "text/css; charset=utf-8"
        path.endsWith(".js") -> "application/javascript; charset=utf-8"
        path.endsWith(".json") -> "application/json; charset=utf-8"
        path.endsWith(".svg") -> "image/svg+xml"
        path.endsWith(".png") -> "image/png"
        path.endsWith(".woff2") -> "font/woff2"
        path.endsWith(".woff") -> "font/woff"
        else -> "application/octet-stream"
    }

    private fun handleRequest(exchange: HttpExchange) {
        exchange.responseHeaders.apply {
            add("Access-Control-Allow-Origin", "*")
            add("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            add("Access-Control-Allow-Headers", "Content-Type")
        }

        if (exchange.requestMethod == "OPTIONS") {
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
            return
        }

        val pathParts = exchange.requestURI.path.split("/").filter { it.isNotEmpty() }
        if (pathParts.size < 3 || pathParts[0] != "idebridge") {
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
            return
        }

        val sessionId = pathParts[1]
        val action = pathParts[2]
        val session = sessions[sessionId]

        val queryParams = parseQuery(exchange.requestURI.rawQuery ?: "")
        val token = queryParams["token"]

        if (session == null || session.token != token) {
            LOG.warn("IdeBridge unauthorized: sessionId=$sessionId action=$action")
            exchange.sendResponseHeaders(401, -1)
            exchange.close()
            return
        }

        when (action) {
            "events" -> handleSSE(exchange, session)
            "send" -> handleSend(exchange, session)
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
        } catch (e: Exception) {
            synchronized(session.sseClients) { session.sseClients.remove(exchange) }
            exchange.closeQuietly()
        }
    }

    private fun handleSend(exchange: HttpExchange, session: BridgeSession) {
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

            messageHandler.handle(session, type, id, payload)

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
                it.closeQuietly()
            }
        }
    }

    private fun parseQuery(query: String): Map<String, String> {
        return query.split("&")
            .filter { it.isNotEmpty() }
            .associate { param ->
                val parts = param.split("=", limit = 2)
                val key = URLDecoder.decode(parts[0], "UTF-8")
                val value = if (parts.size > 1) URLDecoder.decode(parts[1], "UTF-8") else ""
                key to value
            }
    }
}
