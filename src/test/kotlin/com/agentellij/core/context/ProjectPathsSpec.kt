package com.agentellij.core.context

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import java.io.File

class ProjectPathResolverSpec : BehaviorSpec({

    Given("a file path that has to be shown relative to the project") {

        When("the file lives inside the project") {
            val tempDir = tempdir()
            val project = File(tempDir, "project").apply { mkdirs() }
            val file = File(project, "src/Main.kt").apply {
                parentFile.mkdirs()
                writeText("fun main() {}")
            }

            Then("only the part below the project root is kept") {
                ProjectPaths.relativePath(file.path, project.path) shouldBe
                    "src${File.separator}Main.kt"
            }
        }

        When("the file lives outside the project") {
            val tempDir = tempdir()
            val project = File(tempDir, "project").apply { mkdirs() }
            val outside = File(tempDir, "outside.txt").apply { writeText("x") }

            Then("the absolute path is kept as is") {
                ProjectPaths.relativePath(outside.path, project.path) shouldBe
                    outside.absoluteFile.normalize().path
            }
        }

        When("there is no project root to compare against") {
            val file = File(tempdir(), "file.txt").apply { writeText("x") }

            Then("the normalized absolute path is returned") {
                ProjectPaths.relativePath(file.path, null) shouldBe
                    file.absoluteFile.normalize().path
            }
        }
    }

    Given("files dropped onto the chat window") {

        When("the same file arrives twice under different spellings") {
            val tempDir = tempdir()
            val file = File(tempDir, "drop.txt").apply { writeText("x") }

            Then("the duplicate is removed and the path is normalized") {
                ProjectPaths.normalizeDroppedFiles(
                    listOf(file, File(tempDir, "./drop.txt"))
                ) shouldBe listOf(file.absoluteFile.normalize().path)
            }
        }

        When("the drop carries text instead of a file list") {
            val tempDir = tempdir()
            val first = File(tempDir, "first file.txt").apply { writeText("x") }
            val second = File(tempDir, "second.txt").apply { writeText("x") }
            val missing = File(tempDir, "missing.txt")
            val text = "\"${first.path}\"\n${second.path}\n${missing.path}"

            Then("quotes are stripped and paths that do not exist are dropped") {
                ProjectPaths.parseDroppedText(text) { File(it).exists() } shouldBe listOf(
                    first.absoluteFile.normalize().path,
                    second.absoluteFile.normalize().path
                )
            }
        }
    }
})
