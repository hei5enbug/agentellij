package com.agentellij.core.state

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * The shape rules for the JSON state the agent and the web client share.
 *
 * A state file on disk may have been written by an older agent, hand-edited, or
 * corrupted, so every value is reshaped before the web client sees it.
 *
 * None of these functions modify the node they are handed.
 */
internal object StateNormalization {
    private const val THEME = "theme"
    private const val RECENT = "recent"
    private const val FAVORITE = "favorite"
    private const val VARIANT = "variant"
    private val VALID_THEMES = setOf("light", "dark")

    /** Drops a theme the web client would not understand, leaving other settings alone. */
    fun settings(raw: ObjectNode): ObjectNode {
        val normalized = raw.deepCopy()
        if (normalized.has(THEME)) {
            val theme = normalized.get(THEME)
            val valid = theme != null && theme.isTextual && theme.asText() in VALID_THEMES
            if (!valid) normalized.remove(THEME)
        }
        return normalized
    }

    /** Guarantees the three keys the web client indexes into, whatever the file held. */
    fun model(mapper: ObjectMapper, raw: ObjectNode): ObjectNode = mapper.createObjectNode().apply {
        set<JsonNode>(RECENT, arrayOrEmpty(mapper, raw, RECENT))
        set<JsonNode>(FAVORITE, arrayOrEmpty(mapper, raw, FAVORITE))
        set<JsonNode>(VARIANT, objectOrEmpty(mapper, raw, VARIANT))
    }

    fun emptyModel(mapper: ObjectMapper): ObjectNode = model(mapper, mapper.createObjectNode())

    /** Merges an incoming payload key by key onto existing state. */
    fun mergeKv(existing: ObjectNode, payload: JsonNode?): ObjectNode {
        val merged = existing.deepCopy()
        payload?.properties()?.forEach { (key, value) -> merged.set<JsonNode>(key, value) }
        return merged
    }

    /**
     * Applies a model update. Recent and favorite are replaced wholesale because they are
     * ordered lists the client owns; variant is merged because each key is independent.
     */
    fun mergeModel(mapper: ObjectMapper, existing: ObjectNode, payload: JsonNode?): ObjectNode {
        val merged = existing.deepCopy()
        if (payload?.has(RECENT) == true) merged.set<JsonNode>(RECENT, payload.get(RECENT))
        if (payload?.has(FAVORITE) == true) merged.set<JsonNode>(FAVORITE, payload.get(FAVORITE))
        if (payload?.has(VARIANT) == true) {
            val current = (merged.get(VARIANT) as? ObjectNode)?.deepCopy() ?: mapper.createObjectNode()
            payload.get(VARIANT).properties().forEach { (key, value) -> current.set<JsonNode>(key, value) }
            merged.set<JsonNode>(VARIANT, current)
        }
        return merged
    }

    private fun arrayOrEmpty(mapper: ObjectMapper, raw: ObjectNode, field: String): JsonNode =
        if (raw.has(field) && raw.get(field).isArray) raw.get(field) else mapper.createArrayNode()

    private fun objectOrEmpty(mapper: ObjectMapper, raw: ObjectNode, field: String): JsonNode =
        if (raw.has(field) && raw.get(field).isObject) raw.get(field) else mapper.createObjectNode()
}
