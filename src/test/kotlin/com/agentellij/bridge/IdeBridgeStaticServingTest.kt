package com.agentellij.bridge

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.net.HttpURLConnection
import java.net.URI

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdeBridgeStaticServingTest {

    @BeforeAll
    fun setup() {
        IdeBridge.start()
    }

    @AfterAll
    fun teardown() {
        IdeBridge.stop()
    }

    private fun get(path: String): Pair<Int, String> {
        val port = IdeBridge.getPort()
        val conn = URI("http://127.0.0.1:$port$path").toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        val code = conn.responseCode
        val body = try {
            conn.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        }
        conn.disconnect()
        return code to body
    }

    private fun getContentType(path: String): String {
        val port = IdeBridge.getPort()
        val conn = URI("http://127.0.0.1:$port$path").toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        val contentType = conn.contentType ?: ""
        conn.disconnect()
        return contentType
    }

    @Test
    fun `GET ui index html returns 200 with text html`() {
        val (code, body) = get("/ui/index.html")
        assertEquals(200, code)
        assertTrue(body.contains("<!DOCTYPE html") || body.contains("<html"), "Body should contain HTML")
        assertTrue(getContentType("/ui/index.html").contains("text/html"))
    }

    @Test
    fun `GET ui slash defaults to index html`() {
        val (code, _) = get("/ui/")
        assertEquals(200, code)
    }

    @Test
    fun `GET ui without slash defaults to index html`() {
        val (code, _) = get("/ui")
        assertEquals(200, code)
    }

    @Test
    fun `GET nonexistent file returns 404`() {
        val (code, _) = get("/ui/nonexistent.html")
        assertEquals(404, code)
    }

    @Test
    fun `path traversal attack returns 404`() {
        val (code, _) = get("/ui/../META-INF/plugin.xml")
        assertEquals(404, code)
    }

    @Test
    fun `path traversal with encoded dots returns 404`() {
        val (code, _) = get("/ui/%2e%2e/META-INF/plugin.xml")
        assertEquals(404, code)
    }

    @Test
    fun `GET css file returns correct content type`() {
        val (code, _) = get("/ui/css/style.css")
        assertEquals(200, code)
        assertTrue(getContentType("/ui/css/style.css").contains("text/css"))
    }

    @Test
    fun `GET js file returns correct content type`() {
        val (code, _) = get("/ui/js/app.js")
        assertEquals(200, code)
        assertTrue(getContentType("/ui/js/app.js").contains("application/javascript"))
    }

    @Test
    fun `GET vendor js returns 200`() {
        val (code, _) = get("/ui/vendor/marked.min.js")
        assertEquals(200, code)
    }

    @Test
    fun `GET deep nested nonexistent path returns 404`() {
        val (code, _) = get("/ui/a/b/c/d/e.html")
        assertEquals(404, code)
    }

    @Test
    fun `path traversal with backslash returns 404`() {
        val (code, _) = get("/ui/js\\..\\..\\META-INF\\plugin.xml")
        assertEquals(404, code)
    }
}
