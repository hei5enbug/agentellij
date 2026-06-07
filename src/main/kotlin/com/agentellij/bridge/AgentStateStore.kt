package com.agentellij.bridge

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

class AgentStateStore(private val mapper: ObjectMapper) {
    private val logger = Logger.getInstance(AgentStateStore::class.java)
    private val lock = Any()

    fun getKv(statePath: File?): ObjectNode = synchronized(lock) {
        if (statePath == null) mapper.createObjectNode() else readJsonObject(File(statePath, KV_FILE))
    }

    fun updateKv(statePath: File?, payload: JsonNode?): ObjectNode = synchronized(lock) {
        if (statePath == null) return@synchronized mapper.createObjectNode()

        val file = File(statePath, KV_FILE)
        val existing = readJsonObject(file)
        payload?.properties()?.forEach { (key, value) -> existing.set<JsonNode>(key, value) }
        writeJsonObject(file, existing)
        existing
    }

    fun getModel(statePath: File?): ObjectNode = synchronized(lock) {
        if (statePath == null) emptyModelData() else readModelData(File(statePath, MODEL_FILE))
    }

    fun updateModel(statePath: File?, payload: JsonNode?): ObjectNode = synchronized(lock) {
        if (statePath == null) return@synchronized emptyModelData()

        val file = File(statePath, MODEL_FILE)
        val existing = readModelData(file)
        if (payload?.has("recent") == true) existing.set<JsonNode>("recent", payload.get("recent"))
        if (payload?.has("favorite") == true) existing.set<JsonNode>("favorite", payload.get("favorite"))
        if (payload?.has("variant") == true) {
            val current = existing.get("variant") as? ObjectNode ?: mapper.createObjectNode()
            payload.get("variant").properties().forEach { (key, value) -> current.set<JsonNode>(key, value) }
            existing.set<JsonNode>("variant", current)
        }
        writeJsonObject(file, existing)
        existing
    }

    fun getSettings(statePath: File?): ObjectNode = synchronized(lock) {
        if (statePath == null) normalizeSettings(mapper.createObjectNode())
        else normalizeSettings(readJsonObject(File(statePath, SETTINGS_FILE)))
    }

    fun updateSettings(statePath: File?, payload: JsonNode?): ObjectNode = synchronized(lock) {
        if (statePath == null) return@synchronized normalizeSettings(mapper.createObjectNode())

        val current = getSettings(statePath)
        payload?.properties()?.forEach { (key, value) -> current.set<JsonNode>(key, value) }
        val normalized = normalizeSettings(current)
        writeJsonObject(File(statePath, SETTINGS_FILE), normalized)
        normalized
    }

    internal fun readJsonObject(file: File): ObjectNode {
        return try {
            if (file.exists()) {
                val tree = mapper.readTree(file.readText())
                if (tree is ObjectNode) {
                    tree
                } else {
                    backupInvalidJson(file, "Expected JSON object but found ${tree.nodeType}")
                    mapper.createObjectNode()
                }
            } else {
                mapper.createObjectNode()
            }
        } catch (e: Exception) {
            backupInvalidJson(file, "Failed to parse JSON state file", e)
            mapper.createObjectNode()
        }
    }

    internal fun normalizeSettings(raw: ObjectNode): ObjectNode {
        val normalized = raw.deepCopy()
        if (normalized.has("theme")) {
            val theme = normalized.get("theme")
            val valid = theme != null && theme.isTextual && (theme.asText() == "light" || theme.asText() == "dark")
            if (!valid) normalized.remove("theme")
        }
        return normalized
    }

    private fun readModelData(file: File): ObjectNode {
        val raw = readJsonObject(file)
        return mapper.createObjectNode().apply {
            set<JsonNode>("recent", if (raw.has("recent") && raw.get("recent").isArray) raw.get("recent") else mapper.createArrayNode())
            set<JsonNode>("favorite", if (raw.has("favorite") && raw.get("favorite").isArray) raw.get("favorite") else mapper.createArrayNode())
            set<JsonNode>("variant", if (raw.has("variant") && raw.get("variant").isObject) raw.get("variant") else mapper.createObjectNode())
        }
    }

    private fun emptyModelData(): ObjectNode = mapper.createObjectNode().apply {
        set<JsonNode>("recent", mapper.createArrayNode())
        set<JsonNode>("favorite", mapper.createArrayNode())
        set<JsonNode>("variant", mapper.createObjectNode())
    }

    private fun writeJsonObject(file: File, data: ObjectNode) {
        file.parentFile.mkdirs()
        val temp = File.createTempFile(file.name, ".tmp", file.parentFile)
        try {
            temp.writeText(mapper.writeValueAsString(data))
            try {
                Files.move(temp.toPath(), file.toPath(), REPLACE_EXISTING, ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), REPLACE_EXISTING)
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun backupInvalidJson(file: File, message: String, cause: Exception? = null) {
        if (!file.exists()) return
        if (hasBackupWithSameContent(file)) return

        val backup = nextBackupFile(file)
        try {
            Files.copy(file.toPath(), backup.toPath())
            if (cause == null) {
                logger.warn("$message: ${file.absolutePath}. Backup created at ${backup.absolutePath}")
            } else {
                logger.warn("$message: ${file.absolutePath}. Backup created at ${backup.absolutePath}", cause)
            }
        } catch (backupError: Exception) {
            logger.warn("$message: ${file.absolutePath}. Failed to create backup", backupError)
        }
    }

    private fun hasBackupWithSameContent(file: File): Boolean {
        var candidate = File(file.parentFile, "${file.name}.corrupt")
        var index = 0
        while (candidate.exists()) {
            if (sameFileContent(file, candidate)) return true
            index += 1
            candidate = File(file.parentFile, "${file.name}.corrupt.$index")
        }
        return false
    }

    private fun sameFileContent(left: File, right: File): Boolean = try {
        left.readBytes().contentEquals(right.readBytes())
    } catch (_: Exception) {
        false
    }

    private fun nextBackupFile(file: File): File {
        val base = File(file.parentFile, "${file.name}.corrupt")
        if (!base.exists()) return base

        var index = 1
        while (true) {
            val candidate = File(file.parentFile, "${file.name}.corrupt.$index")
            if (!candidate.exists()) return candidate
            index += 1
        }
    }

    private companion object {
        const val KV_FILE = "kv.json"
        const val MODEL_FILE = "model.json"
        const val SETTINGS_FILE = "settings.json"
    }
}
