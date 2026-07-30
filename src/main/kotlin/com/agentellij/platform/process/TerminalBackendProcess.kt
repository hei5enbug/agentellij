package com.agentellij.platform.process

import com.agentellij.platform.env.IdeLoggerDiagnostics
import com.agentellij.core.launch.IncrementalTextCapture
import com.agentellij.core.util.closeQuietly
import com.agentellij.core.util.runQuietly
import com.intellij.openapi.diagnostic.Logger
import com.intellij.terminal.JBTerminalWidget
import com.intellij.terminal.ui.TerminalWidget
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import org.jetbrains.plugins.terminal.ShellTerminalWidget

/**
 * Wraps a [TerminalWidget] to capture its output and expose it as an [InputStream].
 *
 * Output is captured by polling the terminal's text buffer every 200ms.
 * Uses [JBTerminalWidget] (JediTerm) for text buffer access when available.
 */
class TerminalBackendProcess(
    private val widget: TerminalWidget,
    private val outputBuffer: PipedOutputStream
) : BackendProcess {
    private val logger = Logger.getInstance(TerminalBackendProcess::class.java)
    private val diagnostics = IdeLoggerDiagnostics(logger)
    private val alive = AtomicBoolean(true)
    private val capturing = AtomicBoolean(true)
    private val pipedInput = PipedInputStream(outputBuffer)
    private var captureThread: Thread? = null

    private val process: Process?
        get() = runQuietly(diagnostics, "reach the terminal process") {
            widget.ttyConnector?.let { ttyConnector ->
                ShellTerminalWidget.getProcessTtyConnector(ttyConnector)?.process
            }
        }

    /** JediTerm widget for text buffer access — null if running the new block terminal. */
    private val jediTermWidget: JBTerminalWidget? = runQuietly(diagnostics, "reach the terminal text buffer") {
        JBTerminalWidget.asJediTermWidget(widget)
    }

    override val inputStream: InputStream get() = pipedInput

    init {
        startCapture()
    }

    private fun startCapture() {
        captureThread = Thread({
            val capture = IncrementalTextCapture()
            try {
                while (capturing.get() && alive.get()) {
                    val terminalProcess = process
                    if (terminalProcess != null && !terminalProcess.isAlive) {
                        break
                    }

                    try {
                        val newContent = capture.nextChunk(getTerminalText())
                        if (newContent != null) {
                            try {
                                outputBuffer.write(newContent.toByteArray())
                                outputBuffer.flush()
                            } catch (e: Exception) {
                                if (capturing.get()) logger.warn("Error writing to pipe", e)
                                break
                            }
                        }
                        Thread.sleep(POLL_INTERVAL_MILLIS)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    } catch (e: Exception) {
                        logger.warn("Error capturing terminal output", e)
                        try { Thread.sleep(ERROR_BACKOFF_MILLIS) } catch (_: InterruptedException) { break }
                    }
                }
            } finally {
                alive.set(false)
            }
        }, "agentellij-terminal-capture").apply { isDaemon = true }
        captureThread?.start()
    }

    private fun getTerminalText(): String? {
        val jtw = jediTermWidget ?: return null
        return try {
            val terminalTextBuffer = jtw.terminalTextBuffer
            val sb = StringBuilder()
            val historyLines = terminalTextBuffer.historyLinesCount
            for (i in -historyLines until 0) {
                sb.append(terminalTextBuffer.getLine(i).text).append('\n')
            }
            val screenLines = terminalTextBuffer.screenLinesCount
            for (i in 0 until screenLines) {
                sb.append(terminalTextBuffer.getLine(i).text).append('\n')
            }
            sb.toString()
        } catch (e: Exception) {
            logger.debug("Could not read terminal text: ${e.message}")
            null
        }
    }

    override fun stopCapture() {
        capturing.set(false)
        runQuietly(diagnostics, "stop the terminal capture thread") { captureThread?.interrupt() }
        captureThread = null
    }

    override fun destroy() {
        alive.set(false)
        stopCapture()
        try {
            // Try to destroy the underlying process directly
            val ttyConnector = widget.ttyConnector
            if (ttyConnector != null) {
                val terminalProcess = process
                if (terminalProcess != null) {
                    runQuietly(diagnostics, "stop the terminal process") { terminalProcess.destroy() }
                } else {
                    // Fallback: send Ctrl+C through the tty connector
                    runQuietly(diagnostics, "interrupt the terminal process") { ttyConnector.write("\u0003") }
                }
            }
        } catch (e: Exception) {
            logger.warn("Error stopping backend process", e)
        }
        outputBuffer.closeQuietly(diagnostics, "the terminal output buffer")
        pipedInput.closeQuietly(diagnostics, "the terminal output pipe")
    }

    override fun isAlive(): Boolean = process?.isAlive ?: alive.get()

    private companion object {
        const val POLL_INTERVAL_MILLIS = 200L
        const val ERROR_BACKOFF_MILLIS = 500L
    }
}
