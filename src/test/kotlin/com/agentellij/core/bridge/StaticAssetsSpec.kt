package com.agentellij.core.bridge

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class StaticAssetsSpec : BehaviorSpec({

    Given("a request for a bundled web client file") {

        When("the request names the directory itself") {
            Then("a bare prefix falls back to the index page") {
                StaticAssets.resolveResourcePath("/ui") shouldBe "/webui/index.html"
            }

            Then("a trailing slash falls back to the index page") {
                StaticAssets.resolveResourcePath("/ui/") shouldBe "/webui/index.html"
            }
        }

        When("the request names a file") {
            Then("the leading slash is dropped and the resource root is prepended") {
                StaticAssets.resolveResourcePath("/ui/js/app.js") shouldBe "/webui/js/app.js"
            }

            Then("percent-encoded characters are decoded") {
                StaticAssets.resolveResourcePath("/ui/my%20file.css") shouldBe "/webui/my file.css"
            }
        }
    }

    Given("a request that tries to reach outside the bundled files") {

        When("the path contains a parent reference") {
            Then("a plain parent reference is refused") {
                StaticAssets.resolveResourcePath("/ui/../META-INF/plugin.xml").shouldBeNull()
            }

            Then("a percent-encoded parent reference is refused after decoding") {
                StaticAssets.resolveResourcePath("/ui/%2e%2e/META-INF/plugin.xml").shouldBeNull()
            }
        }

        When("the path contains a backslash") {
            Then("a plain backslash is refused") {
                StaticAssets.resolveResourcePath("/ui/js\\..\\plugin.xml").shouldBeNull()
            }

            Then("a percent-encoded backslash is refused after decoding") {
                StaticAssets.resolveResourcePath("/ui/js%5C..%5Cplugin.xml").shouldBeNull()
            }
        }

        When("the percent-encoding itself is malformed") {
            Then("the decoder rejects it by throwing, which the server turns into an error response") {
                shouldThrowAny { StaticAssets.resolveResourcePath("/ui/%zz") }
            }
        }
    }

    Given("a resolved file that has to be labelled for the browser") {

        When("the extension is one the web client uses") {
            Then("html is labelled as html") {
                StaticAssets.mimeTypeFor("/webui/index.html") shouldBe "text/html; charset=utf-8"
            }

            Then("css is labelled as css") {
                StaticAssets.mimeTypeFor("/webui/css/style.css") shouldBe "text/css; charset=utf-8"
            }

            Then("javascript is labelled as javascript") {
                StaticAssets.mimeTypeFor("/webui/js/app.js") shouldBe "application/javascript; charset=utf-8"
            }

            Then("json is labelled as json") {
                StaticAssets.mimeTypeFor("/webui/data.json") shouldBe "application/json; charset=utf-8"
            }

            Then("images and fonts get their own labels") {
                StaticAssets.mimeTypeFor("/webui/icon.svg") shouldBe "image/svg+xml"
                StaticAssets.mimeTypeFor("/webui/icon.png") shouldBe "image/png"
                StaticAssets.mimeTypeFor("/webui/font.woff2") shouldBe "font/woff2"
                StaticAssets.mimeTypeFor("/webui/font.woff") shouldBe "font/woff"
            }
        }

        When("the extension is unknown") {
            Then("the generic binary label is used") {
                StaticAssets.mimeTypeFor("/webui/blob.bin") shouldBe "application/octet-stream"
            }
        }
    }
})
