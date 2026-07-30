package com.agentellij.core.util

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class DebouncedTask(
    private val executor: ScheduledExecutorService,
    private val delayMillis: Long,
    private val task: () -> Unit
) {
    private val lock = Any()
    private var pending: ScheduledFuture<*>? = null

    fun request() {
        synchronized(lock) {
            if (pending?.isDone == false) return
            pending = executor.schedule({
                try {
                    task()
                } finally {
                    synchronized(lock) {
                        pending = null
                    }
                }
            }, delayMillis, TimeUnit.MILLISECONDS)
        }
    }

    fun cancel() {
        synchronized(lock) {
            pending?.cancel(false)
            pending = null
        }
    }
}
