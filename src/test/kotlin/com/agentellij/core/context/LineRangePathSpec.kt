package com.agentellij.core.context

import com.agentellij.core.bridge.OpenFileRequest
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

class LineRangePathSpec : BehaviorSpec({

    Given("a selected range being handed to the agent") {

        When("the selection spans several lines") {
            Then("the notation carries both ends") {
                LineRangePath.format("/tmp/Main.kt", 12, 20) shouldBe "/tmp/Main.kt:12-20"
            }
        }

        When("the selection sits on one line") {
            Then("both ends are the same line rather than being omitted") {
                LineRangePath.format("/tmp/Main.kt", 12, 12) shouldBe "/tmp/Main.kt:12-12"
            }
        }
    }

    Given("a selection whose end offset has to be turned into a line") {

        When("the selection ends part way through the document") {
            Then("the offset steps back one so a trailing line break is not counted") {
                LineRangePath.lastLineLookupOffset(40) shouldBe 39
            }
        }

        When("the selection is empty at the very start of the document") {
            Then("the offset never goes negative") {
                LineRangePath.lastLineLookupOffset(0) shouldBe 0
            }
        }
    }

    Given("the notation being read back by the bridge that opens the file") {

        When("a formatted range is parsed again") {
            Then("the path and both line numbers survive the round trip") {
                checkAll(Arb.int(1..100_000), Arb.int(1..100_000)) { start, end ->
                    val target = OpenFileRequest.parse(
                        LineRangePath.format("/tmp/Main.kt", start, end),
                        lineFromPayload = -1
                    )

                    target.path shouldBe "/tmp/Main.kt"
                    target.startLine shouldBe start - 1
                    target.endLine shouldBe end - 1
                }
            }
        }

        When("the path itself contains characters that look like a range") {
            Then("only the trailing range is consumed") {
                val target = OpenFileRequest.parse(
                    LineRangePath.format("/tmp/build-1.2/Main.kt", 5, 9),
                    lineFromPayload = -1
                )

                target.path shouldBe "/tmp/build-1.2/Main.kt"
                target.startLine shouldBe 4
                target.endLine shouldBe 8
            }
        }
    }
})
