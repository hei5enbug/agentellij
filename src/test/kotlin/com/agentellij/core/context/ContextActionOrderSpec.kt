package com.agentellij.core.context

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

private data class Candidate(val name: String, val kind: ContextActionKind?)

class ContextActionOrderSpec : BehaviorSpec({

    val lines = Candidate("lines", ContextActionKind.LINES)
    val file = Candidate("file", ContextActionKind.FILE)
    val directory = Candidate("directory", ContextActionKind.DIRECTORY)
    val foreign = Candidate("someone else's action", null)

    fun promote(candidates: List<Candidate>, editorFocused: Boolean) =
        ContextActionOrder.promote(candidates, editorFocused) { it.kind }

    Given("the shared shortcut pressed while the editor has focus") {

        When("all three of our actions are candidates") {
            Then("the most specific choice, the selected lines, wins") {
                promote(listOf(file, directory, lines), editorFocused = true).first() shouldBe lines
            }

            Then("the full order runs from most to least specific") {
                promote(listOf(directory, file, lines), editorFocused = true) shouldBe
                    listOf(lines, file, directory)
            }
        }

        When("the lines action is not a candidate") {
            Then("the current file wins over the tree selection") {
                promote(listOf(directory, file), editorFocused = true).first() shouldBe file
            }
        }
    }

    Given("the shared shortcut pressed outside the editor") {

        When("all three of our actions are candidates") {
            Then("the tree selection wins") {
                promote(listOf(lines, file, directory), editorFocused = false) shouldBe
                    listOf(directory, file, lines)
            }
        }

        When("only a single file is selected in the tree") {
            Then("the tree action still wins over the single-file action") {
                promote(listOf(file, directory), editorFocused = false).first() shouldBe directory
            }
        }
    }

    Given("candidates that are not ours") {

        When("none of the candidates belong to us") {
            Then("we express no opinion at all") {
                promote(listOf(foreign), editorFocused = true).shouldBeEmpty()
            }

            Then("an empty candidate list also yields no opinion") {
                promote(emptyList(), editorFocused = false).shouldBeEmpty()
            }
        }

        When("our actions are mixed with someone else's") {
            val promoted = promote(listOf(foreign, file, directory), editorFocused = false)

            Then("ours are moved to the front") {
                promoted.first() shouldBe directory
            }

            Then("the other action is kept rather than dropped") {
                promoted shouldContain foreign
            }

            Then("the other action sits behind ours") {
                promoted.last() shouldBe foreign
            }
        }
    }
})
