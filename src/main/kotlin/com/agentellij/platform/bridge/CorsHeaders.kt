package com.agentellij.platform.bridge

import com.agentellij.core.bridge.CorsPolicy
import com.sun.net.httpserver.HttpExchange

/**
 * Applies the cross-origin decision to a real exchange.
 *
 * Returns false when the request was refused, in which case the response has already
 * been sent and the caller must stop.
 */
internal object CorsHeaders {

    fun apply(exchange: HttpExchange, allowedMethods: String): Boolean {
        val decision = CorsPolicy.decide(exchange.requestHeaders.getFirst("Origin"))
        if (!decision.allowed) {
            exchange.sendResponseHeaders(403, -1)
            exchange.close()
            return false
        }

        exchange.responseHeaders.apply {
            decision.allowOrigin?.let { add("Access-Control-Allow-Origin", it) }
            add("Access-Control-Allow-Methods", allowedMethods)
            add("Access-Control-Allow-Headers", "Content-Type")
        }
        return true
    }
}
