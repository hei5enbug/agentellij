package com.agentellij.platform.process

import com.agentellij.core.launch.CaptureOutcome
import com.agentellij.core.launch.StreamCaptureLoop
import com.agentellij.core.util.closeQuietly
import com.agentellij.platform.env.IdeLoggerDiagnostics
import com.intellij.openapi.diagnostic.Logger
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class DirectBackendProcess(
    private val process: Process,
    private val outputBuffer: PipedOutputStream
) : BackendProcess {
    private val logger = Logger.getInstance(DirectBackendProcess::class.java)
    private val diagnostics = IdeLoggerDiagnostics(logger)
    private val alive = AtomicBoolean(true)
    private val capturing = AtomicBoolean(true)
    private val pipedInput = PipedInputStream(outputBuffer)
    private var captureThread: Thread? = null

    override val inputStream: InputStream get() = pipedInput

    init {
        startCapture()
    }

    private fun startCapture() {
        captureThread = Thread({
            val outcome = StreamCaptureLoop.run(
                source = process.inputStream,
                shouldContinue = { capturing.get() && alive.get() },
                sink = { chunk, length ->
                    outputBuffer.write(chunk, 0, length)
                    outputBuffer.flush()
                },
                diagnostics = diagnostics
            )
            // Restoring the flag is the thread owner's job, so core only reports it.
            if (outcome == CaptureOutcome.INTERRUPTED) Thread.currentThread().interrupt()
        }, "agentellij-direct-capture").apply { isDaemon = true }
        captureThread?.start()
    }

    override fun stopCapture() {
        capturing.set(false)
        captureThread?.interrupt()
        captureThread = null
    }

    override fun destroy() {
        alive.set(false)
        stopCapture()
        try {
            process.destroyForcibly()
        } catch (e: Exception) {
            logger.warn("Error destroying process", e)
        }
        outputBuffer.closeQuietly(diagnostics, "the agent output buffer")
        pipedInput.closeQuietly(diagnostics, "the agent output pipe")
    }

    override fun isAlive(): Boolean = alive.get() && process.isAlive
}
