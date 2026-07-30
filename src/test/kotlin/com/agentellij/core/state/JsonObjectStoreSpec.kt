package com.agentellij.core.state

import com.agentellij.core.util.Diagnostics
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

private class RecordingDiagnostics : Diagnostics {
    val warnings = mutableListOf<String>()
    override fun warn(message: String, cause: Throwable?) {
        warnings += message
    }
}

class JsonObjectStoreSpec : BehaviorSpec({

    val mapper = jacksonObjectMapper()

    Given("a state file being read") {

        When("the file does not exist yet") {
            Then("an empty object is returned rather than failing") {
                JsonObjectStore(mapper).read(File(tempdir(), "kv.json")).size() shouldBe 0
            }
        }

        When("the file holds a valid object") {
            val dir = tempdir()
            File(dir, "kv.json").writeText("""{"a":1}""")

            Then("its contents are returned") {
                JsonObjectStore(mapper).read(File(dir, "kv.json")).get("a").asInt() shouldBe 1
            }
        }

        When("the file holds text that is not JSON") {
            val dir = tempdir()
            val file = File(dir, "kv.json").apply { writeText("not json") }
            val diagnostics = RecordingDiagnostics()
            val data = JsonObjectStore(mapper, diagnostics).read(file)

            Then("an empty object is returned") {
                data.size() shouldBe 0
            }

            Then("the unusable file is backed up rather than lost") {
                File(dir, "kv.json.corrupt").readText() shouldBe "not json"
            }

            Then("the problem is reported rather than swallowed silently") {
                diagnostics.warnings shouldHaveSize 1
            }
        }

        When("the file holds JSON that is not an object") {
            val dir = tempdir()
            val file = File(dir, "kv.json").apply { writeText("[]") }
            val data = JsonObjectStore(mapper).read(file)

            Then("an empty object is returned") {
                data.size() shouldBe 0
            }

            Then("the file is backed up too") {
                File(dir, "kv.json.corrupt").exists() shouldBe true
            }
        }
    }

    Given("a state file that has already been backed up once") {

        When("a different broken version appears") {
            val dir = tempdir()
            val file = File(dir, "kv.json").apply { writeText("not json") }
            File(dir, "kv.json.corrupt").writeText("previous")
            JsonObjectStore(mapper).read(file)

            Then("the earlier backup is left untouched") {
                File(dir, "kv.json.corrupt").readText() shouldBe "previous"
            }

            Then("the new backup takes the next number") {
                File(dir, "kv.json.corrupt.1").readText() shouldBe "not json"
            }
        }

        When("the same broken version is read again") {
            val dir = tempdir()
            val file = File(dir, "kv.json").apply { writeText("not json") }
            val store = JsonObjectStore(mapper)
            store.read(file)
            store.read(file)

            Then("the existing backup is reused instead of piling up copies") {
                File(dir, "kv.json.corrupt").exists() shouldBe true
                File(dir, "kv.json.corrupt.1").exists() shouldBe false
            }
        }
    }

    Given("a state file being written") {

        When("the write succeeds") {
            val dir = tempdir()
            val file = File(dir, "kv.json")
            JsonObjectStore(mapper).write(file, mapper.readTree("""{"a":1}""") as com.fasterxml.jackson.databind.node.ObjectNode)

            Then("the content reaches the file") {
                mapper.readTree(file.readText()).get("a").asInt() shouldBe 1
            }

            Then("no temporary file is left behind") {
                dir.listFiles()!!.filter { it.name.endsWith(".tmp") }.shouldBeEmpty()
            }
        }

        When("the parent directory does not exist yet") {
            val file = File(tempdir(), "nested/deeper/kv.json")
            JsonObjectStore(mapper).write(file, mapper.createObjectNode().put("a", 1))

            Then("it is created on the way") {
                file.exists() shouldBe true
            }
        }

        When("the filesystem refuses an atomic move") {
            val dir = tempdir()
            val file = File(dir, "kv.json")
            var plainMoveUsed = false
            val store = JsonObjectStore(
                mapper = mapper,
                moveAtomically = { _, _ -> throw AtomicMoveNotSupportedException(null, null, "unsupported") },
                movePlainly = { source, target ->
                    plainMoveUsed = true
                    Files.move(source, target, REPLACE_EXISTING)
                }
            )
            store.write(file, mapper.createObjectNode().put("a", 1))

            Then("the plain move is used instead") {
                plainMoveUsed shouldBe true
            }

            Then("the content still reaches the file") {
                mapper.readTree(file.readText()).get("a").asInt() shouldBe 1
            }

            Then("no temporary file is left behind") {
                dir.listFiles()!!.filter { it.name.endsWith(".tmp") }.shouldBeEmpty()
            }
        }
    }
})
