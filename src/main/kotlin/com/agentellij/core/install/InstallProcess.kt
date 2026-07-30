package com.agentellij.core.install

import java.io.InputStream

/**
 * The operations the installer needs from a running process.
 *
 * Going through a port rather than [Process] directly is what lets a test drive the
 * timeout and the forced-kill path without actually waiting minutes for them.
 */
internal interface InstallProcess {
    val output: InputStream

    fun isAlive(): Boolean

    /** Waits up to [timeoutMillis]; returns true when the process has exited. */
    fun awaitExit(timeoutMillis: Long): Boolean

    fun exitCode(): Int

    fun destroy()

    fun destroyForcibly()
}

/** Whether the user has asked to stop. Checked between polls. */
internal fun interface CancellationSignal {
    fun isCancelled(): Boolean
}

/**
 * How an install attempt ended.
 *
 * Cancellation is a result rather than an exception so that the pure layer does not
 * depend on the IDE's cancellation type. The platform adapter turns it back into the
 * exception the IDE expects.
 */
internal data class InstallOutcome(
    val exitCode: Int,
    val output: String,
    val cancelled: Boolean = false
)
