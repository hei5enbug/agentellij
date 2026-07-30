package com.agentellij.core.util

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DebouncedTaskSpec : BehaviorSpec({

    Given("a task that coalesces bursts of requests") {

        When("several requests arrive before the delay elapses") {
            val executor = Executors.newSingleThreadScheduledExecutor()
            val count = AtomicInteger(0)
            val latch = CountDownLatch(1)
            val task = DebouncedTask(executor, 50) {
                count.incrementAndGet()
                latch.countDown()
            }

            try {
                task.request()
                task.request()
                task.request()
                latch.await(1, TimeUnit.SECONDS)

                Then("the work runs exactly once") {
                    count.get() shouldBe 1
                }
            } finally {
                executor.shutdownNow()
            }
        }

        When("a new request arrives after the previous one finished") {
            val executor = Executors.newSingleThreadScheduledExecutor()
            val count = AtomicInteger(0)
            val first = CountDownLatch(1)
            val second = CountDownLatch(1)
            val task = DebouncedTask(executor, 10) {
                if (count.incrementAndGet() == 1) first.countDown() else second.countDown()
            }

            try {
                task.request()
                first.await(1, TimeUnit.SECONDS)
                task.request()
                second.await(1, TimeUnit.SECONDS)

                Then("the work is scheduled again") {
                    count.get() shouldBe 2
                }
            } finally {
                executor.shutdownNow()
            }
        }

        When("a pending request is cancelled") {
            val executor = Executors.newSingleThreadScheduledExecutor()
            val count = AtomicInteger(0)
            val task = DebouncedTask(executor, 200) { count.incrementAndGet() }

            try {
                task.request()
                task.cancel()
                Thread.sleep(300)

                Then("the work never runs") {
                    count.get() shouldBe 0
                }
            } finally {
                executor.shutdownNow()
            }
        }
    }
})
