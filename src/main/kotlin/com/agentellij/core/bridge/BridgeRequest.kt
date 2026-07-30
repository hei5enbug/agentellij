package com.agentellij.core.bridge

import java.net.URLDecoder

/** The session and action a bridge request addresses, once the path has been understood. */
internal data class BridgeRequestTarget(val sessionId: String, val action: String)

/**
 * Understands the shape of a bridge request without touching the network.
 *
 * The wire format is `/idebridge/{sessionId}/{action}?token={token}`.
 */
internal object BridgeRequest {
    const val CONTEXT_PATH = "idebridge"
    const val ACTION_EVENTS = "events"
    const val ACTION_SEND = "send"
    private const val TOKEN_PARAMETER = "token"

    /** Returns null when the path does not address a session and an action. */
    fun parseTarget(path: String): BridgeRequestTarget? {
        val parts = path.split("/").filter { it.isNotEmpty() }
        if (parts.size < 3 || parts[0] != CONTEXT_PATH) return null
        return BridgeRequestTarget(sessionId = parts[1], action = parts[2])
    }

    fun parseQuery(query: String): Map<String, String> =
        query.split("&")
            .filter { it.isNotEmpty() }
            .associate { parameter ->
                val parts = parameter.split("=", limit = 2)
                val key = URLDecoder.decode(parts[0], "UTF-8")
                val value = if (parts.size > 1) URLDecoder.decode(parts[1], "UTF-8") else ""
                key to value
            }

    fun tokenOf(rawQuery: String?): String? = parseQuery(rawQuery.orEmpty())[TOKEN_PARAMETER]

    /** An absent session and a mismatched token are both unauthorized. */
    fun isAuthorized(sessionToken: String?, presentedToken: String?): Boolean =
        sessionToken != null && sessionToken == presentedToken
}
