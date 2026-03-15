package com.agentellij.backend

import com.intellij.openapi.diagnostic.Logger
import com.intellij.terminal.JBTerminalWidget
import com.intellij.terminal.ui.TerminalWidget
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import com.agentellij.util.closeQuietly
import com.agentellij.util.runQuietly

/**
 * Wraps a [TerminalWidget] to capture its output and expose it as an [InputStream].
 *
 * Output is captured by polling the terminal's text buffer every 200ms.
 * Uses [JBTerminalWidget] (JediTerm) for text buffer access when available.
 */
class TerminalBackendProcess(
    private val widget: TerminalWidget,
    private val command: String,
    private val outputBuffer: PipedOutputStream
) : BackendProcess {
    private val logger = Logger.getInstance(TerminalBackendProcess::class.java)
    private val alive = AtomicBoolean(true)
    private val capturing = AtomicBoolean(true)
    private val pipedInput = PipedInputStream(outputBuffer)
    private var captureThread: Thread? = null

    /** JediTerm widget for text buffer access — null if running the new block terminal. */
    private val jediTermWidget: JBTerminalWidget? = runQuietly {
        JBTerminalWidget.asJediTermWidget(widget)
    }

    override val inputStream: InputStream get() = pipedInput

    init {
        startCapture()
    }

    private fun startCapture() {
        captureThread = Thread({
            var lastLength = 0
            while (capturing.get() && alive.get()) {
                try {
                    val text = getTerminalText()
                    if (text != null && text.length > lastLength) {
                        val newContent = text.substring(lastLength)
                        lastLength = text.length
                        try {
                            outputBuffer.write(newContent.toByteArray())
                            outputBuffer.flush()
                        } catch (e: Exception) {
                            if (capturing.get()) logger.warn("Error writing to pipe", e)
                            break
                        }
                    }
                    Thread.sleep(200)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (e: Exception) {
                    logger.warn("Error capturing terminal output", e)
                    try { Thread.sleep(500) } catch (_: InterruptedException) { break }
                }
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
        runQuietly { captureThread?.interrupt() }
        captureThread = null
    }

    override fun destroy() {
        alive.set(false)
        stopCapture()
        try {
            // Try to destroy the underlying process directly
            val ttyConnector = widget.ttyConnector
            if (ttyConnector != null) {
                val processTty = ShellTerminalWidget.getProcessTtyConnector(ttyConnector)
                if (processTty != null) {
                    runQuietly { processTty.process.destroy() }
                } else {
                    // Fallback: send Ctrl+C through the tty connector
                    runQuietly { ttyConnector.write("\u0003") }
                }
            }
        } catch (e: Exception) {
            logger.warn("Error stopping backend process", e)
        }
        outputBuffer.closeQuietly()
        pipedInput.closeQuietly()
    }

    override fun isAlive(): Boolean = alive.get()
}
