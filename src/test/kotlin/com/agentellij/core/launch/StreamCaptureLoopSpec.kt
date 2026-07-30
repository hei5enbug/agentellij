package com.agentellij.core.launch

import com.agentellij.core.util.Diagnostics
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

private class RecordingDiagnostics : Diagnostics {
    val warnings = mutableListOf<String>()
    override fun warn(message: String, cause: Throwable?) {
        warnings += message
    }
}

private class FailingStream(private val error: Exception) : InputStream() {
    override fun read(): Int = throw error
    override fun read(b: ByteArray, off: Int, len: Int): Int = throw error
}

class StreamCaptureLoopSpec : BehaviorSpec({

    fun capture(
        source: InputStream,
        shouldContinue: () -> Boolean = { true },
        sink: (ByteArray, Int) -> Unit = { _, _ -> },
        diagnostics: Diagnostics = Diagnostics.NONE
    ): CaptureOutcome = StreamCaptureLoop.run(source, shouldContinue, sink, diagnostics)

    Given("agent output being copied to the tool window") {

        When("the agent writes and then exits") {
            val received = StringBuilder()
            capture(
                source = ByteArrayInputStream("hello".toByteArray()),
                sink = { chunk, length -> received.append(String(chunk, 0, length)) }
            )

            Then("everything written is passed on") {
                received.toString() shouldBe "hello"
            }

            Then("the loop ends when the stream ends") {
                capture(source = ByteArrayInputStream("hello".toByteArray())) shouldBe CaptureOutcome.FINISHED
            }
        }

        When("the tool window asks the capture to stop") {
            var reads = 0
            val received = StringBuilder()
            capture(
                source = ByteArrayInputStream(ByteArray(64 * 1024)),
                shouldContinue = { reads++ == 0 },
                sink = { chunk, length -> received.append(String(chunk, 0, length)) }
            )

            Then("no further reads happen after the flag flips") {
                reads shouldBe 2
            }
        }

        When("the receiving end has gone away") {
            var sinkCalls = 0
            capture(
                source = ByteArrayInputStream(ByteArray(64 * 1024)),
                sink = { _, _ ->
                    sinkCalls += 1
                    throw IOException("pipe closed")
                }
            )

            Then("the loop stops instead of spinning against a dead pipe") {
                sinkCalls shouldBe 1
            }
        }
    }

    Given("a stream that fails while the capture is still wanted") {

        When("reading throws") {
            val diagnostics = RecordingDiagnostics()
            capture(source = FailingStream(IOException("stream broke")), diagnostics = diagnostics)

            Then("the failure is reported rather than swallowed") {
                diagnostics.warnings shouldHaveSize 1
            }

            Then("the outcome says the read failed") {
                capture(source = FailingStream(IOException("stream broke"))) shouldBe CaptureOutcome.FAILED
            }
        }

        When("reading throws after the capture was already told to stop") {
            val diagnostics = RecordingDiagnostics()
            var calls = 0
            capture(
                source = FailingStream(IOException("stream broke")),
                shouldContinue = { calls++ < 1 },
                diagnostics = diagnostics
            )

            Then("the expected shutdown noise is not reported as a problem") {
                diagnostics.warnings shouldHaveSize 0
            }
        }

        When("the capture thread is interrupted") {
            val outcome = capture(source = FailingStream(InterruptedException()))

            Then("the interruption is reported so the thread's owner can restore the flag") {
                outcome shouldBe CaptureOutcome.INTERRUPTED
            }

            Then("this spec's own thread is left untouched") {
                Thread.currentThread().isInterrupted shouldBe false
            }
        }
    }
})

class IncrementalTextCaptureSpec : BehaviorSpec({

    Given("a terminal screen being polled repeatedly") {

        When("new text has appeared since the last poll") {
            val capture = IncrementalTextCapture()

            Then("the first poll emits everything") {
                capture.nextChunk("hello") shouldBe "hello"
            }

            Then("the next poll emits only what was added") {
                capture.nextChunk("hello world") shouldBe " world"
            }
        }

        When("nothing has changed since the last poll") {
            val capture = IncrementalTextCapture()
            capture.nextChunk("hello")

            Then("nothing is emitted") {
                capture.nextChunk("hello").shouldBeNull()
            }
        }

        When("the screen got shorter, because it was cleared or scrolled") {
            val capture = IncrementalTextCapture()
            capture.nextChunk("hello world")

            Then("nothing is emitted, so old text is not repeated") {
                capture.nextChunk("hi").shouldBeNull()
            }
        }

        When("the screen could not be read") {
            val capture = IncrementalTextCapture()
            capture.nextChunk("hello")

            Then("nothing is emitted") {
                capture.nextChunk(null).shouldBeNull()
            }

            Then("the next successful read still only emits the addition") {
                capture.nextChunk("hello!") shouldBe "!"
            }
        }
    }
})
