package com.agentellij.bridge

import com.sun.net.httpserver.HttpExchange
import java.net.URI

internal object BridgeCorsPolicy {
    fun apply(exchange: HttpExchange, allowedMethods: String): Boolean {
        val origin = exchange.requestHeaders.getFirst("Origin")
        if (origin != null && !isAllowedOrigin(origin)) {
            exchange.sendResponseHeaders(403, -1)
            exchange.close()
            return false
        }

        exchange.responseHeaders.apply {
            if (origin != null) add("Access-Control-Allow-Origin", origin)
            add("Access-Control-Allow-Methods", allowedMethods)
            add("Access-Control-Allow-Headers", "Content-Type")
        }
        return true
    }

    internal fun isAllowedOrigin(origin: String): Boolean = try {
        val uri = URI(origin)
        val schemeAllowed = uri.scheme == "http" || uri.scheme == "https"
        val host = uri.host?.lowercase()
        schemeAllowed && (host == "127.0.0.1" || host == "localhost" || host == "::1" || host == "[::1]")
    } catch (_: Exception) {
        false
    }
}
