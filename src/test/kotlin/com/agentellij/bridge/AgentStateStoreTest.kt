package com.agentellij.bridge

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AgentStateStoreTest {
    private val mapper = jacksonObjectMapper()
    private val store = AgentStateStore(mapper)

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `kv update merges payload and persists result`() {
        File(tempDir, "kv.json").writeText("{\"existing\":1}")
        val payload = mapper.readTree("{\"next\":2}")

        val updated = store.updateKv(tempDir, payload)
        val persisted = mapper.readTree(File(tempDir, "kv.json").readText())

        assertEquals(1, updated.get("existing").asInt())
        assertEquals(2, updated.get("next").asInt())
        assertEquals(2, persisted.get("next").asInt())
    }

    @Test
    fun `invalid json reads as empty object`() {
        val file = File(tempDir, "kv.json")
        file.writeText("not json")

        val data = store.readJsonObject(file)

        assertEquals(0, data.size())
        assertTrue(File(tempDir, "kv.json.corrupt").exists())
    }

    @Test
    fun `non object json reads as empty object and creates backup`() {
        val file = File(tempDir, "kv.json")
        file.writeText("[]")

        val data = store.readJsonObject(file)

        assertEquals(0, data.size())
        assertTrue(File(tempDir, "kv.json.corrupt").exists())
    }

    @Test
    fun `invalid json backup avoids overwriting existing backup`() {
        val file = File(tempDir, "kv.json")
        file.writeText("not json")
        File(tempDir, "kv.json.corrupt").writeText("previous")

        store.readJsonObject(file)

        assertEquals("previous", File(tempDir, "kv.json.corrupt").readText())
        assertTrue(File(tempDir, "kv.json.corrupt.1").exists())
    }

    @Test
    fun `invalid json backup is reused for unchanged corrupt content`() {
        val file = File(tempDir, "kv.json")
        file.writeText("not json")

        store.readJsonObject(file)
        store.readJsonObject(file)

        assertTrue(File(tempDir, "kv.json.corrupt").exists())
        assertFalse(File(tempDir, "kv.json.corrupt.1").exists())
    }

    @Test
    fun `model get normalizes missing fields to arrays and object`() {
        File(tempDir, "model.json").writeText("{\"recent\":{},\"favorite\":[\"a\"],\"variant\":[]}")

        val model = store.getModel(tempDir)

        assertTrue(model.get("recent").isArray)
        assertEquals(0, model.get("recent").size())
        assertTrue(model.get("favorite").isArray)
        assertEquals("a", model.get("favorite").get(0).asText())
        assertTrue(model.get("variant").isObject)
    }

    @Test
    fun `model update merges variant fields`() {
        File(tempDir, "model.json").writeText("{\"variant\":{\"provider\":\"old\"}}")
        val payload = mapper.readTree("{\"variant\":{\"model\":\"new\"}}")

        val updated = store.updateModel(tempDir, payload)

        assertEquals("old", updated.get("variant").get("provider").asText())
        assertEquals("new", updated.get("variant").get("model").asText())
    }

    @Test
    fun `settings remove invalid theme while keeping valid settings`() {
        File(tempDir, "settings.json").writeText("{\"theme\":\"solarized\",\"fontSize\":14}")

        val settings = store.getSettings(tempDir)

        assertFalse(settings.has("theme"))
        assertEquals(14, settings.get("fontSize").asInt())
    }

    @Test
    fun `settings update keeps valid dark theme`() {
        val payload = mapper.readTree("{\"theme\":\"dark\",\"compact\":true}")

        val settings = store.updateSettings(tempDir, payload)

        assertEquals("dark", settings.get("theme").asText())
        assertTrue(settings.get("compact").asBoolean())
    }

    @Test
    fun `null state path returns empty default data`() {
        assertEquals(0, store.getKv(null).size())
        assertEquals(0, store.getSettings(null).size())
        assertTrue(store.getModel(null).get("recent").isArray)
        assertTrue(store.getModel(null).get("favorite").isArray)
        assertTrue(store.getModel(null).get("variant").isObject)
    }
}
