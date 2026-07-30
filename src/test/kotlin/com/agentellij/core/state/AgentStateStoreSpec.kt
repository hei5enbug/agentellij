package com.agentellij.core.state

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import java.io.File

class AgentStateStoreSpec : BehaviorSpec({

    val mapper = jacksonObjectMapper()
    val store = AgentStateStore(mapper)

    Given("an agent that keeps its state on disk") {

        When("key-value state is updated") {
            val dir = tempdir()
            File(dir, "kv.json").writeText("""{"existing":1}""")
            val updated = store.updateKv(dir, mapper.readTree("""{"next":2}"""))

            Then("the update is merged onto what was already there") {
                updated.get("existing").asInt() shouldBe 1
                updated.get("next").asInt() shouldBe 2
            }

            Then("the merged result reaches the disk") {
                mapper.readTree(File(dir, "kv.json").readText()).get("next").asInt() shouldBe 2
            }
        }

        When("model state is read from a file with the wrong shapes") {
            val dir = tempdir()
            File(dir, "model.json").writeText("""{"recent":{},"favorite":["a"],"variant":[]}""")
            val model = store.getModel(dir)

            Then("the shapes are corrected before the web client sees them") {
                model.get("recent").isArray shouldBe true
                model.get("variant").isObject shouldBe true
            }

            Then("valid data is preserved") {
                model.get("favorite").get(0).asText() shouldBe "a"
            }
        }

        When("part of the model variant is updated") {
            val dir = tempdir()
            File(dir, "model.json").writeText("""{"variant":{"provider":"old"}}""")
            val updated = store.updateModel(dir, mapper.readTree("""{"variant":{"model":"new"}}"""))

            Then("the update merges rather than replaces") {
                updated.get("variant").get("provider").asText() shouldBe "old"
                updated.get("variant").get("model").asText() shouldBe "new"
            }

            Then("the merged result reaches the disk") {
                val persisted = mapper.readTree(File(dir, "model.json").readText())
                persisted.get("variant").get("provider").asText() shouldBe "old"
            }
        }

        When("settings are read with a theme the client cannot use") {
            val dir = tempdir()
            File(dir, "settings.json").writeText("""{"theme":"solarized","fontSize":14}""")
            val settings = store.getSettings(dir)

            Then("the unusable theme is dropped and the rest is kept") {
                settings.has("theme") shouldBe false
                settings.get("fontSize").asInt() shouldBe 14
            }
        }

        When("settings are updated with a valid theme") {
            val dir = tempdir()
            val settings = store.updateSettings(dir, mapper.readTree("""{"theme":"dark","compact":true}"""))

            Then("both the theme and the other settings are stored") {
                settings.get("theme").asText() shouldBe "dark"
                settings.get("compact").asBoolean() shouldBe true
            }
        }

        When("settings are updated with a theme the client cannot use") {
            val dir = tempdir()
            val settings = store.updateSettings(dir, mapper.readTree("""{"theme":"solarized"}"""))

            Then("the update is normalized on the way in as well") {
                settings.has("theme") shouldBe false
            }
        }

        When("a payload node supplied by the caller is used for an update") {
            val dir = tempdir()
            val payload = mapper.readTree("""{"next":2}""")
            store.updateKv(dir, payload)

            Then("the caller's node is not modified") {
                payload.size() shouldBe 1
            }
        }
    }

    Given("an agent that keeps no state on disk") {

        When("its state is requested") {
            Then("key-value state is empty") {
                store.getKv(null).size() shouldBe 0
            }

            Then("settings are empty") {
                store.getSettings(null).size() shouldBe 0
            }

            Then("model state still has the three keys the web client indexes into") {
                val model = store.getModel(null)
                model.get("recent").isArray shouldBe true
                model.get("favorite").isArray shouldBe true
                model.get("variant").isObject shouldBe true
            }
        }

        When("an update is attempted anyway") {
            Then("it is absorbed without failing") {
                store.updateKv(null, mapper.readTree("""{"a":1}""")).size() shouldBe 0
                store.updateSettings(null, mapper.readTree("""{"theme":"dark"}""")).size() shouldBe 0
                store.updateModel(null, mapper.readTree("""{"recent":["a"]}""")).get("recent").size() shouldBe 0
            }
        }
    }
})
