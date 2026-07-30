package com.agentellij.core.install

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Runs a user-approved install command and reports how it went.
 *
 * Three guards matter here, and all three exist because the command is arbitrary and
 * comes from a package manager: the captured output is capped so a chatty installer
 * cannot exhaust memory, the run is bounded so a stalled installer does not hang the
 * IDE forever, and cancellation actually kills the process rather than just abandoning
 * it.
 *
 * The clock is injected so those bounds can be exercised in a test without waiting for
 * them in real time.
 */
internal object InstallRunner {
    const val OUTPUT_LIMIT_BYTES = 64 * 1024
    const val TIMEOUT_MILLIS = 10L * 60L * 1000L
    private const val POLL_MILLIS = 250L
    private const val DESTROY_GRACE_MILLIS = 2_000L
    private const val OUTPUT_DRAIN_MILLIS = 1_000L

    fun run(
        startProcess: () -> InstallProcess,
        cancellation: CancellationSignal,
        nowMillis: () -> Long
    ): InstallOutcome {
        return try {
            val process = startProcess()
            val capture = OutputCapture(process.output, OUTPUT_LIMIT_BYTES).apply { start() }
            val deadline = nowMillis() + TIMEOUT_MILLIS

            while (process.isAlive()) {
                if (cancellation.isCancelled()) {
                    destroy(process)
                    return InstallOutcome(exitCode = -1, output = capture.text(), cancelled = true)
                }
                if (nowMillis() >= deadline) {
                    destroy(process)
                    return InstallOutcome(-1, "Installer timed out after ${TIMEOUT_MILLIS / 60_000} minutes.")
                }
                process.awaitExit(POLL_MILLIS)
            }

            capture.awaitCompletion(OUTPUT_DRAIN_MILLIS)
            InstallOutcome(process.exitCode(), capture.text())
        } catch (e: Exception) {
            InstallOutcome(-1, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun destroy(process: InstallProcess) {
        process.destroy()
        if (!process.awaitExit(DESTROY_GRACE_MILLIS)) {
            process.destroyForcibly()
        }
    }
}

/**
 * Drains a process output stream on a background thread, keeping at most [limitBytes].
 *
 * The read has to happen while the process runs: a process that fills its output buffer
 * and is never read blocks forever.
 */
private class OutputCapture(private val stream: InputStream, private val limitBytes: Int) {
    private val buffer = ByteArrayOutputStream()
    private var thread: Thread? = null

    fun start() {
        thread = Thread {
            stream.use { input ->
                val chunk = ByteArray(4096)
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    synchronized(buffer) {
                        val remaining = limitBytes - buffer.size()
                        if (remaining > 0) buffer.write(chunk, 0, minOf(read, remaining))
                    }
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun awaitCompletion(timeoutMillis: Long) {
        thread?.join(timeoutMillis)
    }

    fun text(): String = synchronized(buffer) { buffer.toString(Charsets.UTF_8) }
}
