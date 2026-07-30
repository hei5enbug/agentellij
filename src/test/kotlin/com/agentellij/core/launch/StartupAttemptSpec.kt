package com.agentellij.core.launch

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private class FakeTimer {
    var cancelled = false
        private set

    fun cancel() {
        cancelled = true
    }
}

private class FakeBackend {
    var destroyed = false
        private set

    fun destroy() {
        destroyed = true
    }
}

class StartupAttemptSpec : BehaviorSpec({

    Given("an attempt that is still running") {

        When("it has just begun") {
            val attempt = StartupAttempt<FakeTimer, FakeBackend>()

            Then("it is pending") {
                attempt.isPending() shouldBe true
                attempt.currentPhase() shouldBe StartupPhase.PENDING
            }

            Then("it holds no resources yet") {
                attempt.timerHandle().shouldBeNull()
                attempt.processHandle().shouldBeNull()
            }
        }

        When("its timer and process are handed over") {
            val attempt = StartupAttempt<FakeTimer, FakeBackend>()
            val timer = FakeTimer()
            val backend = FakeBackend()

            Then("both are accepted") {
                attempt.attachTimer(timer) shouldBe true
                attempt.attachProcess(backend) shouldBe true
            }

            Then("both can be read back") {
                attempt.attachTimer(timer)
                attempt.attachProcess(backend)
                attempt.timerHandle() shouldBe timer
                attempt.processHandle() shouldBe backend
            }
        }
    }

    Given("an attempt that connects and times out at the same moment") {

        When("the connection wins the race") {
            val attempt = StartupAttempt<FakeTimer, FakeBackend>()

            Then("the connection is recorded") {
                attempt.markConnected() shouldBe true
            }

            Then("the timeout is refused, so the user is not shown a failure after success") {
                attempt.markConnected()
                attempt.markTimedOut() shouldBe false
                attempt.currentPhase() shouldBe StartupPhase.CONNECTED
            }
        }

        When("the timeout wins the race") {
            val attempt = StartupAttempt<FakeTimer, FakeBackend>()
            attempt.markTimedOut()

            Then("a late connection is refused") {
                attempt.markConnected() shouldBe false
                attempt.currentPhase() shouldBe StartupPhase.TIMED_OUT
            }
        }
    }

    Given("an attempt that has been abandoned") {
        val attempt = StartupAttempt<FakeTimer, FakeBackend>()
        val timer = FakeTimer()
        val backend = FakeBackend()
        attempt.attachTimer(timer)
        attempt.attachProcess(backend)
        val (releasedTimer, releasedBackend) = attempt.release()

        When("it is released") {
            Then("its resources are handed back so the caller can dispose of them") {
                releasedTimer shouldBe timer
                releasedBackend shouldBe backend
            }

            Then("it holds nothing afterwards, so a second release is harmless") {
                attempt.timerHandle().shouldBeNull()
                attempt.processHandle().shouldBeNull()
            }

            Then("it can no longer connect or time out") {
                attempt.markConnected() shouldBe false
                attempt.markTimedOut() shouldBe false
            }
        }

        When("a resource arrives after it was released") {
            Then("the timer is refused, so the caller knows to cancel it") {
                attempt.attachTimer(FakeTimer()) shouldBe false
                attempt.timerHandle().shouldBeNull()
            }

            Then("the process is refused, so the caller knows to destroy it") {
                attempt.attachProcess(FakeBackend()) shouldBe false
                attempt.processHandle().shouldBeNull()
            }
        }
    }
})
