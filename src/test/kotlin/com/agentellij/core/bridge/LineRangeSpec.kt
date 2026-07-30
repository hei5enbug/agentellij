package com.agentellij.core.bridge

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class LineRangeSpec : BehaviorSpec({

    Given("a requested range being fitted onto a document") {

        When("the range sits inside the document") {
            val range = LineRange.clamp(startLine = 2, endLine = 5, lineCount = 100)

            Then("both ends are kept") {
                range.startLine shouldBe 2
                range.endLine shouldBe 5
            }

            Then("a selection is made") {
                range.selects shouldBe true
            }
        }

        When("no end line was requested") {
            val range = LineRange.clamp(startLine = 4, endLine = -1, lineCount = 100)

            Then("the caret moves without selecting anything") {
                range.startLine shouldBe 4
                range.endLine shouldBe 4
                range.selects shouldBe false
            }
        }

        When("the range points past the end of the document") {
            val range = LineRange.clamp(startLine = 500, endLine = 900, lineCount = 10)

            Then("both ends are pulled back to the last line") {
                range.startLine shouldBe 9
                range.endLine shouldBe 9
            }

            Then("nothing is selected because the range collapsed") {
                range.selects shouldBe false
            }
        }

        When("the end line is before the start line") {
            val range = LineRange.clamp(startLine = 8, endLine = 3, lineCount = 100)

            Then("the end is pulled up to the start") {
                range.startLine shouldBe 8
                range.endLine shouldBe 8
                range.selects shouldBe false
            }
        }

        When("the document is empty") {
            val range = LineRange.clamp(startLine = 5, endLine = 9, lineCount = 0)

            Then("the range collapses onto the first line rather than going negative") {
                range.startLine shouldBe 0
                range.endLine shouldBe 0
                range.selects shouldBe false
            }
        }

        When("a negative start line arrives") {
            val range = LineRange.clamp(startLine = -3, endLine = -1, lineCount = 10)

            Then("it is pulled up to the first line") {
                range.startLine shouldBe 0
            }
        }
    }
})
