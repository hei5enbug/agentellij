package com.agentellij.platform.bridge

import com.sun.net.httpserver.HttpExchange

/**
 * A connected web client: the token it must present, and the event streams it holds
 * open.
 *
 * Which project the session serves is tracked separately, because authentication does
 * not depend on it.
 */
data class BridgeSession(
    val id: String,
    val token: String,
    val sseClients: MutableSet<HttpExchange>,
    val lastCompletionAt: MutableMap<String, Long>
)
