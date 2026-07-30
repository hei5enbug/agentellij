package com.agentellij.core.install

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.ByteArrayInputStream
import java.io.InputStream

private class FakeInstallProcess(
    outputBytes: ByteArray = ByteArray(0),
    private val exitCode: Int = 0,
    stayAlive: Boolean = false
) : InstallProcess {
    @Volatile
    private var alive = stayAlive

    var destroyCalls = 0
        private set
    var forcibleDestroyCalls = 0
        private set

    /** Set to false to model a process that ignores a polite termination request. */
    var diesPolitely = true

    override val output: InputStream = ByteArrayInputStream(outputBytes)

    override fun isAlive(): Boolean = alive

    override fun awaitExit(timeoutMillis: Long): Boolean = !alive

    override fun exitCode(): Int = exitCode

    override fun destroy() {
        destroyCalls += 1
        if (diesPolitely) alive = false
    }

    override fun destroyForcibly() {
        forcibleDestroyCalls += 1
        alive = false
    }
}

private class SteppingClock(private var current: Long = 0) {
    var step: Long = 0
    fun now(): Long {
        val value = current
        current += step
        return value
    }
}

class InstallRunnerSpec : BehaviorSpec({

    val neverCancelled = CancellationSignal { false }

    Given("an install command the user approved") {

        When("the installer finishes successfully") {
            val process = FakeInstallProcess(outputBytes = "done".toByteArray(), exitCode = 0)
            val outcome = InstallRunner.run({ process }, neverCancelled, { 0 })

            Then("the exit code is reported") {
                outcome.exitCode shouldBe 0
            }

            Then("the output is captured") {
                outcome.output shouldBe "done"
            }

            Then("it is not reported as cancelled") {
                outcome.cancelled shouldBe false
            }
        }

        When("the installer fails") {
            val outcome = InstallRunner.run(
                { FakeInstallProcess(outputBytes = "npm ERR!".toByteArray(), exitCode = 7) },
                neverCancelled,
                { 0 }
            )

            Then("the failing exit code is reported") {
                outcome.exitCode shouldBe 7
            }

            Then("the output is kept so the user can see why") {
                outcome.output shouldBe "npm ERR!"
            }
        }

        When("the installer produces more output than we are willing to hold") {
            val outcome = InstallRunner.run(
                { FakeInstallProcess(outputBytes = ByteArray(70 * 1024) { 'x'.code.toByte() }) },
                neverCancelled,
                { 0 }
            )

            Then("the captured output is capped") {
                outcome.output.length shouldBe InstallRunner.OUTPUT_LIMIT_BYTES
            }
        }

        When("the process cannot be started at all") {
            val outcome = InstallRunner.run(
                { throw java.io.IOException("Cannot run program \"npm\"") },
                neverCancelled,
                { 0 }
            )

            Then("the failure is reported rather than thrown at the caller") {
                outcome.exitCode shouldBe -1
                outcome.output shouldContain "Cannot run program"
            }
        }
    }

    Given("an installer that keeps running") {

        When("the user cancels") {
            val process = FakeInstallProcess(stayAlive = true)
            val outcome = InstallRunner.run({ process }, { true }, { 0 })

            Then("the run reports cancellation") {
                outcome.cancelled shouldBe true
            }

            Then("the process is not left running") {
                process.destroyCalls shouldBe 1
            }
        }

        When("the time limit passes") {
            val process = FakeInstallProcess(stayAlive = true)
            val clock = SteppingClock().apply { step = InstallRunner.TIMEOUT_MILLIS + 1 }
            val outcome = InstallRunner.run({ process }, neverCancelled, clock::now)

            Then("the run reports a timeout") {
                outcome.exitCode shouldBe -1
                outcome.output shouldContain "timed out"
            }

            Then("the process is stopped") {
                process.destroyCalls shouldBe 1
            }
        }

        When("the process ignores a polite termination request") {
            val process = FakeInstallProcess(stayAlive = true).apply { diesPolitely = false }
            InstallRunner.run({ process }, { true }, { 0 })

            Then("it is asked politely first") {
                process.destroyCalls shouldBe 1
            }

            Then("it is then killed") {
                process.forcibleDestroyCalls shouldBe 1
            }
        }
    }
})
