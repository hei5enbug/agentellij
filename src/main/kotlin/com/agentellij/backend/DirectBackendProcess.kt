package com.agentellij.backend

import com.agentellij.util.closeQuietly
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
            try {
                val reader = process.inputStream
                val buf = ByteArray(4096)
                while (capturing.get() && alive.get()) {
                    val n = reader.read(buf)
                    if (n == -1) break
                    try {
                        outputBuffer.write(buf, 0, n)
                        outputBuffer.flush()
                    } catch (_: Exception) {
                        break
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                if (capturing.get()) logger.warn("Capture error", e)
            }
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
        outputBuffer.closeQuietly()
        pipedInput.closeQuietly()
    }

    override fun isAlive(): Boolean = alive.get() && process.isAlive
}
