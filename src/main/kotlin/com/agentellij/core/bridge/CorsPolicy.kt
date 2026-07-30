package com.agentellij.core.bridge

import java.net.URI

/** What a request's Origin header means for the response. */
internal data class CorsDecision(val allowed: Boolean, val allowOrigin: String?)

/**
 * Decides who may talk to the bridge.
 *
 * The bridge carries IDE commands, so only the machine it runs on may reach it. A
 * request with no Origin header is not a cross-origin browser request and is let
 * through without an allow header.
 */
internal object CorsPolicy {

    private val LOOPBACK_HOSTS = setOf("127.0.0.1", "localhost", "::1", "[::1]")
    private val ALLOWED_SCHEMES = setOf("http", "https")

    fun decide(origin: String?): CorsDecision = when {
        origin == null -> CorsDecision(allowed = true, allowOrigin = null)
        isAllowedOrigin(origin) -> CorsDecision(allowed = true, allowOrigin = origin)
        else -> CorsDecision(allowed = false, allowOrigin = null)
    }

    fun isAllowedOrigin(origin: String): Boolean = try {
        val uri = URI(origin)
        uri.scheme in ALLOWED_SCHEMES && uri.host?.lowercase() in LOOPBACK_HOSTS
    } catch (_: Exception) {
        false
    }
}
