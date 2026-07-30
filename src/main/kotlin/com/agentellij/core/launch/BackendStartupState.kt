package com.agentellij.core.launch

import java.util.concurrent.atomic.AtomicReference

/** How far a single start-up attempt has got. */
enum class StartupPhase { PENDING, CONNECTED, TIMED_OUT, RELEASED }

/**
 * One attempt at starting an agent, and the resources that belong to it.
 *
 * Starting an agent is slow and can be retried while an earlier attempt is still in
 * flight. Tracking attempts with a shared counter is not enough: a stale callback can
 * pass a "still current?" check and then act after a newer attempt has replaced the
 * resources, cancelling a timer that is no longer its own.
 *
 * Each attempt therefore owns its timer and its process. A stale attempt can only reach
 * what it owns, so acting late is harmless.
 *
 * @param T the timer handle type, so the pure layer does not name a scheduler type.
 * @param P the process handle type, for the same reason.
 */
class StartupAttempt<T, P> {
    private val phase = AtomicReference(StartupPhase.PENDING)
    private val timer = AtomicReference<T?>(null)
    private val process = AtomicReference<P?>(null)

    fun currentPhase(): StartupPhase = phase.get()

    fun isPending(): Boolean = phase.get() == StartupPhase.PENDING

    /**
     * Hands the timer to the attempt.
     *
     * Returns false when the attempt is already over, in which case the caller must
     * cancel what it just created. Without this the window between starting a timer and
     * storing it would let a released attempt keep one running.
     */
    fun attachTimer(handle: T): Boolean {
        timer.set(handle)
        if (phase.get() == StartupPhase.RELEASED) {
            timer.set(null)
            return false
        }
        return true
    }

    /** Hands the process to the attempt, with the same guarantee as [attachTimer]. */
    fun attachProcess(handle: P): Boolean {
        process.set(handle)
        if (phase.get() == StartupPhase.RELEASED) {
            process.set(null)
            return false
        }
        return true
    }

    fun timerHandle(): T? = timer.get()

    fun processHandle(): P? = process.get()

    /** Only one of connecting and timing out may win. */
    fun markConnected(): Boolean = phase.compareAndSet(StartupPhase.PENDING, StartupPhase.CONNECTED)

    fun markTimedOut(): Boolean = phase.compareAndSet(StartupPhase.PENDING, StartupPhase.TIMED_OUT)

    /** Ends the attempt and yields its resources so the caller can dispose of them. */
    fun release(): Pair<T?, P?> {
        phase.set(StartupPhase.RELEASED)
        return timer.getAndSet(null) to process.getAndSet(null)
    }
}
