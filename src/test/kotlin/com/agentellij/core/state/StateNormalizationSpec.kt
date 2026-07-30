package com.agentellij.core.state

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class StateNormalizationSpec : BehaviorSpec({

    val mapper = jacksonObjectMapper()
    fun json(text: String): ObjectNode = mapper.readTree(text) as ObjectNode

    Given("settings coming back from the agent state file") {

        When("the theme is one the web client understands") {
            Then("a light theme survives") {
                StateNormalization.settings(json("""{"theme":"light"}""")).get("theme").asText() shouldBe "light"
            }

            Then("a dark theme survives") {
                StateNormalization.settings(json("""{"theme":"dark"}""")).get("theme").asText() shouldBe "dark"
            }
        }

        When("the theme is something else") {
            Then("an unknown name is dropped") {
                StateNormalization.settings(json("""{"theme":"solarized"}""")).has("theme") shouldBe false
            }

            Then("a value that is not text is dropped") {
                StateNormalization.settings(json("""{"theme":7}""")).has("theme") shouldBe false
            }
        }

        When("there is no theme at all") {
            Then("nothing is added") {
                StateNormalization.settings(json("""{"fontSize":14}""")).has("theme") shouldBe false
            }
        }

        When("other settings are present") {
            Then("they are left untouched") {
                StateNormalization.settings(json("""{"theme":"nope","fontSize":14}""")).get("fontSize").asInt() shouldBe 14
            }
        }

        When("normalization runs") {
            val original = json("""{"theme":"nope"}""")
            StateNormalization.settings(original)

            Then("the node it was handed is not modified") {
                original.has("theme") shouldBe true
            }
        }
    }

    Given("model data coming back from the agent state file") {

        When("the three keys are missing entirely") {
            val model = StateNormalization.model(mapper, json("{}"))

            Then("all three are supplied with empty values") {
                model.get("recent").isArray shouldBe true
                model.get("favorite").isArray shouldBe true
                model.get("variant").isObject shouldBe true
            }
        }

        When("a key holds the wrong kind of value") {
            val model = StateNormalization.model(mapper, json("""{"recent":{},"favorite":"x","variant":[]}"""))

            Then("a non-array recent list becomes an empty array") {
                model.get("recent").isArray shouldBe true
                model.get("recent").size() shouldBe 0
            }

            Then("a non-array favorite list becomes an empty array") {
                model.get("favorite").isArray shouldBe true
            }

            Then("a non-object variant becomes an empty object") {
                model.get("variant").isObject shouldBe true
            }
        }

        When("the values are already the right shape") {
            val model = StateNormalization.model(mapper, json("""{"recent":["a"],"favorite":["b"],"variant":{"c":1}}"""))

            Then("they are carried through unchanged") {
                model.get("recent").get(0).asText() shouldBe "a"
                model.get("favorite").get(0).asText() shouldBe "b"
                model.get("variant").get("c").asInt() shouldBe 1
            }
        }
    }

    Given("an update arriving for key-value state") {

        When("the payload adds and overwrites keys") {
            val merged = StateNormalization.mergeKv(json("""{"a":1,"b":2}"""), json("""{"b":3,"c":4}"""))

            Then("untouched keys survive") {
                merged.get("a").asInt() shouldBe 1
            }

            Then("overlapping keys are replaced") {
                merged.get("b").asInt() shouldBe 3
            }

            Then("new keys are added") {
                merged.get("c").asInt() shouldBe 4
            }
        }

        When("there is no payload") {
            Then("the existing state is returned as it was") {
                StateNormalization.mergeKv(json("""{"a":1}"""), null).get("a").asInt() shouldBe 1
            }
        }

        When("the merge runs") {
            val existing = json("""{"a":1}""")
            val payload = json("""{"b":2}""")
            StateNormalization.mergeKv(existing, payload)

            Then("neither node it was handed is modified") {
                existing.has("b") shouldBe false
                payload.size() shouldBe 1
            }
        }
    }

    Given("an update arriving for model state") {

        When("the payload names recent or favorite") {
            val merged = StateNormalization.mergeModel(
                mapper,
                StateNormalization.model(mapper, json("""{"recent":["old"],"favorite":["keep"]}""")),
                json("""{"recent":["new"]}""")
            )

            Then("the named list is replaced wholesale") {
                merged.get("recent").size() shouldBe 1
                merged.get("recent").get(0).asText() shouldBe "new"
            }

            Then("the list that was not named is left alone") {
                merged.get("favorite").get(0).asText() shouldBe "keep"
            }
        }

        When("the payload names part of the variant") {
            val merged = StateNormalization.mergeModel(
                mapper,
                StateNormalization.model(mapper, json("""{"variant":{"provider":"old"}}""")),
                json("""{"variant":{"model":"new"}}""")
            )

            Then("the untouched variant key survives") {
                merged.get("variant").get("provider").asText() shouldBe "old"
            }

            Then("the new variant key is added") {
                merged.get("variant").get("model").asText() shouldBe "new"
            }
        }

        When("the merge runs") {
            val existing = StateNormalization.model(mapper, json("""{"variant":{"provider":"old"}}"""))
            StateNormalization.mergeModel(mapper, existing, json("""{"variant":{"model":"new"}}"""))

            Then("the node it was handed is not modified") {
                existing.get("variant").has("model") shouldBe false
            }
        }
    }
})
