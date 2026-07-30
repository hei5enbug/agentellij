package com.agentellij.core.bridge

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * Builds the JSON envelopes the web client expects on the event stream.
 *
 * The clock is passed in rather than read, so an envelope can be pinned by a test.
 * Nothing here knows about sessions or sockets: delivery belongs to the server.
 */
internal object MessageEnvelope {

    fun event(mapper: ObjectMapper, type: String, payload: Map<String, Any?>, timestamp: Long): ObjectNode =
        mapper.createObjectNode().apply {
            put("type", type)
            set<JsonNode>("payload", mapper.valueToTree(payload))
            put("timestamp", timestamp)
        }

    /** Returns null when there is no request to reply to, which means nothing is sent. */
    fun success(mapper: ObjectMapper, id: String?, timestamp: Long): ObjectNode? {
        if (id == null) return null
        return mapper.createObjectNode().apply {
            put("replyTo", id)
            put("ok", true)
            put("timestamp", timestamp)
        }
    }

    fun failure(mapper: ObjectMapper, id: String?, error: String, timestamp: Long): ObjectNode? {
        if (id == null) return null
        return mapper.createObjectNode().apply {
            put("replyTo", id)
            put("ok", false)
            put("error", error)
            put("timestamp", timestamp)
        }
    }

    fun payload(mapper: ObjectMapper, id: String?, payload: Any, timestamp: Long): ObjectNode? {
        if (id == null) return null
        return mapper.createObjectNode().apply {
            put("replyTo", id)
            put("ok", true)
            set<JsonNode>("payload", mapper.valueToTree(payload))
            put("timestamp", timestamp)
        }
    }
}
