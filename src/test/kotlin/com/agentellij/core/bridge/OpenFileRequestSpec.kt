package com.agentellij.core.bridge

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class OpenFileRequestSpec : BehaviorSpec({

    Given("a path the web client wants opened") {

        When("the path carries no range suffix") {
            val target = OpenFileRequest.parse("/tmp/Main.kt", lineFromPayload = -1)

            Then("the path is used as it stands") {
                target.path shouldBe "/tmp/Main.kt"
            }

            Then("no line is requested") {
                target.startLine shouldBe -1
                target.endLine shouldBe -1
            }
        }

        When("the path carries a single line suffix") {
            val target = OpenFileRequest.parse("/tmp/Main.kt:12", lineFromPayload = -1)

            Then("the suffix is stripped from the path") {
                target.path shouldBe "/tmp/Main.kt"
            }

            Then("the one-based line becomes a zero-based line") {
                target.startLine shouldBe 11
            }

            Then("no end line is requested") {
                target.endLine shouldBe -1
            }
        }

        When("the path carries a range suffix") {
            val target = OpenFileRequest.parse("/tmp/Main.kt:12-20", lineFromPayload = -1)

            Then("the suffix is stripped from the path") {
                target.path shouldBe "/tmp/Main.kt"
            }

            Then("both ends become zero-based") {
                target.startLine shouldBe 11
                target.endLine shouldBe 19
            }
        }

        When("a line is supplied alongside the path as well") {
            val target = OpenFileRequest.parse("/tmp/Main.kt:12", lineFromPayload = 30)

            Then("the separately supplied line wins") {
                target.startLine shouldBe 29
            }

            Then("the suffix is still stripped from the path") {
                target.path shouldBe "/tmp/Main.kt"
            }
        }

        When("the separately supplied line is not a real line") {
            Then("a zero is ignored in favour of the suffix") {
                OpenFileRequest.parse("/tmp/Main.kt:12", lineFromPayload = 0).startLine shouldBe 11
            }
        }

        When("a windows path carries a drive letter") {
            val target = OpenFileRequest.parse("C:\\src\\Main.kt:12", lineFromPayload = -1)

            Then("only the trailing range is stripped, not the drive colon") {
                target.path shouldBe "C:\\src\\Main.kt"
                target.startLine shouldBe 11
            }
        }
    }
})
