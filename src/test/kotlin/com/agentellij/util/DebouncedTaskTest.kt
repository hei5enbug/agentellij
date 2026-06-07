package com.agentellij.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DebouncedTaskTest {
    @Test
    fun `multiple requests before delay run once`() {
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

            assertEquals(1, count.get())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `request after completed task schedules again`() {
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

            assertEquals(2, count.get())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `cancel prevents pending execution`() {
        val executor = Executors.newSingleThreadScheduledExecutor()
        val count = AtomicInteger(0)
        val task = DebouncedTask(executor, 200) { count.incrementAndGet() }

        try {
            task.request()
            task.cancel()
            Thread.sleep(300)

            assertEquals(0, count.get())
        } finally {
            executor.shutdownNow()
        }
    }
}
