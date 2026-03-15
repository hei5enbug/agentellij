package com.agentellij.bridge

import com.intellij.openapi.project.Project
import com.sun.net.httpserver.HttpExchange
import java.util.Collections

data class BridgeSession(
    val id: String,
    val token: String,
    val project: Project,
    val sseClients: MutableSet<HttpExchange> = Collections.synchronizedSet(mutableSetOf())
)

data class SessionInfo(val baseUrl: String, val token: String, val sessionId: String)
