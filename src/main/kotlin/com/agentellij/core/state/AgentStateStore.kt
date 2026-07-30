package com.agentellij.core.state

import com.agentellij.core.util.Diagnostics
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.File

/**
 * Read-through access to the JSON state files the agent keeps on disk.
 *
 * The plugin does not own this state, it only proxies it for the web client, so nothing
 * is cached between calls. An agent that keeps no state on disk yields empty values.
 *
 * The state directory is supplied per call by whoever knows which agent is active.
 */
class AgentStateStore(
    private val mapper: ObjectMapper,
    diagnostics: Diagnostics = Diagnostics.NONE
) {
    private val files = JsonObjectStore(mapper, diagnostics)
    private val lock = Any()

    fun getKv(statePath: File?): ObjectNode = synchronized(lock) {
        if (statePath == null) mapper.createObjectNode() else files.read(File(statePath, KV_FILE))
    }

    fun updateKv(statePath: File?, payload: JsonNode?): ObjectNode = synchronized(lock) {
        if (statePath == null) return@synchronized mapper.createObjectNode()

        val file = File(statePath, KV_FILE)
        val merged = StateNormalization.mergeKv(files.read(file), payload)
        files.write(file, merged)
        merged
    }

    fun getModel(statePath: File?): ObjectNode = synchronized(lock) {
        if (statePath == null) {
            StateNormalization.emptyModel(mapper)
        } else {
            StateNormalization.model(mapper, files.read(File(statePath, MODEL_FILE)))
        }
    }

    fun updateModel(statePath: File?, payload: JsonNode?): ObjectNode = synchronized(lock) {
        if (statePath == null) return@synchronized StateNormalization.emptyModel(mapper)

        val file = File(statePath, MODEL_FILE)
        val existing = StateNormalization.model(mapper, files.read(file))
        val merged = StateNormalization.mergeModel(mapper, existing, payload)
        files.write(file, merged)
        merged
    }

    fun getSettings(statePath: File?): ObjectNode = synchronized(lock) {
        if (statePath == null) {
            StateNormalization.settings(mapper.createObjectNode())
        } else {
            StateNormalization.settings(files.read(File(statePath, SETTINGS_FILE)))
        }
    }

    fun updateSettings(statePath: File?, payload: JsonNode?): ObjectNode = synchronized(lock) {
        if (statePath == null) {
            return@synchronized StateNormalization.settings(mapper.createObjectNode())
        }

        val file = File(statePath, SETTINGS_FILE)
        val current = StateNormalization.settings(files.read(file))
        val merged = StateNormalization.mergeKv(current, payload)
        val normalized = StateNormalization.settings(merged)
        files.write(file, normalized)
        normalized
    }

    private companion object {
        const val KV_FILE = "kv.json"
        const val MODEL_FILE = "model.json"
        const val SETTINGS_FILE = "settings.json"
    }
}
