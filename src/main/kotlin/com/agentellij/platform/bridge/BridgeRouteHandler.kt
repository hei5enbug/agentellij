package com.agentellij.platform.bridge

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.project.Project

/**
 * Runs one message from the web client.
 *
 * The server takes this rather than reaching for the real handler, so the HTTP layer
 * can be exercised end to end without an IDE project behind it.
 */
internal fun interface BridgeRouteHandler {
    fun handle(session: BridgeSession, project: Project?, type: String?, id: String?, payload: JsonNode?)
}
