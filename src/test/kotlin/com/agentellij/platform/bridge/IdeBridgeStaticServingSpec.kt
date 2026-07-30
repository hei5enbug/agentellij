package com.agentellij.platform.bridge

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.net.HttpURLConnection
import java.net.URI

/**
 * Runs against a real loopback server. The pure path and MIME decisions are covered by
 * unit specs; this spec exists to prove that the web client assets actually ship inside
 * the plugin jar and are reachable, which no unit test can show.
 */
class IdeBridgeStaticServingSpec : BehaviorSpec({

    beforeSpec { IdeBridge.start() }
    afterSpec { IdeBridge.stop() }

    fun open(path: String): HttpURLConnection {
        val port = IdeBridge.getPort()
        return (URI("http://127.0.0.1:$port$path").toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
        }
    }

    fun status(path: String): Int = open(path).let { conn ->
        val code = conn.responseCode
        conn.disconnect()
        code
    }

    fun body(path: String): String = open(path).let { conn ->
        val text = runCatching { conn.inputStream.bufferedReader().readText() }
            .getOrElse { conn.errorStream?.bufferedReader()?.readText().orEmpty() }
        conn.disconnect()
        text
    }

    fun contentType(path: String): String = open(path).let { conn ->
        val type = conn.contentType.orEmpty()
        conn.disconnect()
        type
    }

    Given("the bundled web client served over the loopback bridge") {

        When("the index page is requested") {
            Then("it is served successfully") {
                status("/ui/index.html") shouldBe 200
            }

            Then("the body really is the web client markup") {
                body("/ui/index.html") shouldContain "<html"
            }

            Then("it is labelled as html") {
                contentType("/ui/index.html") shouldContain "text/html"
            }
        }

        When("the directory itself is requested") {
            Then("a trailing slash falls back to the index page") {
                status("/ui/") shouldBe 200
            }

            Then("no trailing slash falls back too") {
                status("/ui") shouldBe 200
            }
        }

        When("an asset is requested") {
            Then("the stylesheet is served as css") {
                status("/ui/css/style.css") shouldBe 200
                contentType("/ui/css/style.css") shouldContain "text/css"
            }

            Then("the application script is served as javascript") {
                status("/ui/js/app.js") shouldBe 200
                contentType("/ui/js/app.js") shouldContain "application/javascript"
            }

            Then("a vendored script is served too") {
                status("/ui/vendor/marked.min.js") shouldBe 200
            }
        }

        When("a file that does not exist is requested") {
            Then("a plain missing name is rejected") {
                status("/ui/nonexistent.html") shouldBe 404
            }

            Then("a deeply nested missing path is rejected") {
                status("/ui/a/b/c/d/e.html") shouldBe 404
            }
        }

        When("someone tries to escape the asset directory") {
            Then("a literal parent reference is rejected") {
                status("/ui/../META-INF/plugin.xml") shouldBe 404
            }

            Then("a percent-encoded parent reference is rejected") {
                status("/ui/%2e%2e/META-INF/plugin.xml") shouldBe 404
            }

            Then("an encoded backslash walk is rejected") {
                status("/ui/js%5C..%5C..%5CMETA-INF%5Cplugin.xml") shouldBe 404
            }
        }
    }
})
