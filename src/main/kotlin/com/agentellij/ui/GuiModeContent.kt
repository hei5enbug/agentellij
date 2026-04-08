package com.agentellij.ui

import com.agentellij.backend.BackendLauncher
import com.agentellij.backend.BackendProcess
import com.agentellij.backend.AgentProfileResolver
import com.agentellij.bridge.IdeBridge
import com.agentellij.context.DragDropHandler
import com.agentellij.settings.AgentellIJConfigurable
import com.agentellij.util.closeQuietly
import com.agentellij.util.runQuietly
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.UIManager

class GuiModeContent(
    private val project: Project,
    private val toolWindow: ToolWindow
) {
    companion object {
        private val logger = Logger.getInstance(GuiModeContent::class.java)
    }

    private val profile = AgentProfileResolver.resolve()
    private val maxLogChars = 200_000

    fun install() {
        val mainPanel = JPanel(BorderLayout())
        val content = toolWindow.contentManager.factory.createContent(mainPanel, "", false)
        toolWindow.contentManager.addContent(content)

        if (!JBCefApp.isSupported()) {
            mainPanel.add(JLabel("<html><center>JCEF is not supported on this platform.<br/>Please use a JetBrains Runtime that includes JCEF.</center></html>"), BorderLayout.CENTER)
            return
        }

        val (logArea, hideableLogs) = createLogComponents()

        val procRef = AtomicReference<BackendProcess?>(null)
        val browserRef = AtomicReference<JBCefBrowser?>(null)
        val connected = AtomicBoolean(false)
        val launchGeneration = AtomicInteger(0)
        val timeoutRef = AtomicReference<ScheduledFuture<*>?>(null)
        val logLock = Any()
        val logBuffer = StringBuilder()
        val logFlushScheduled = AtomicBoolean(false)

        fun scheduleLogFlush() {
            if (!logFlushScheduled.compareAndSet(false, true)) return
            SwingUtilities.invokeLater {
                val chunk = synchronized(logLock) {
                    val s = logBuffer.toString()
                    logBuffer.setLength(0)
                    s
                }
                logArea.append(chunk)
                val doc = logArea.document
                val overflow = doc.length - maxLogChars
                if (overflow > 0) runQuietly { doc.remove(0, overflow) }
                logFlushScheduled.set(false)
                val hasMore = synchronized(logLock) { logBuffer.isNotEmpty() }
                if (hasMore) scheduleLogFlush()
            }
        }

        fun queueLog(line: String) {
            synchronized(logLock) { logBuffer.append(line).append('\n') }
            scheduleLogFlush()
        }

        val timeoutMs = 300_000L

        fun startBackend() {
            val gen = launchGeneration.incrementAndGet()

            timeoutRef.getAndSet(null)?.cancel(false)
            runQuietly { procRef.getAndSet(null)?.destroy() }
            browserRef.getAndSet(null)?.let { runQuietly { Disposer.dispose(it) } }
            connected.set(false)

            showLoading(mainPanel, hideableLogs)

            val timeout = AppExecutorUtil.getAppScheduledExecutorService().schedule({
                if (connected.get() || launchGeneration.get() != gen) return@schedule
                logger.warn("Backend connection timeout after ${timeoutMs}ms")
                SwingUtilities.invokeLater {
                    if (launchGeneration.get() != gen) return@invokeLater
                    showRecoverableError(mainPanel, hideableLogs, "Backend connection timeout.<br/>Check logs for details.", { startBackend() })
                }
                runQuietly { procRef.get()?.destroy() }
            }, timeoutMs, TimeUnit.MILLISECONDS)
            timeoutRef.set(timeout)

            AppExecutorUtil.getAppExecutorService().execute {
                if (launchGeneration.get() != gen) return@execute

                val proc = try {
                    BackendLauncher.launchBackend(project)
                } catch (e: Exception) {
                    logger.error("Failed to launch backend", e)
                    if (launchGeneration.get() != gen) return@execute
                    SwingUtilities.invokeLater {
                        if (launchGeneration.get() != gen) return@invokeLater
                        showRecoverableError(mainPanel, hideableLogs, "Failed to start backend:<br/>${e.message}<br/><br/>Is the agent binary installed and on your PATH?", { startBackend() })
                    }
                    timeoutRef.get()?.cancel(false)
                    return@execute
                }

                if (launchGeneration.get() != gen) {
                    runQuietly { proc.destroy() }
                    return@execute
                }
                procRef.set(proc)

                val reader = BufferedReader(InputStreamReader(proc.inputStream, StandardCharsets.UTF_8))
                val logThread = Thread {
                    try {
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val l = line!!.trim()
                            queueLog(l)
                            if (connected.get()) continue

                            val serverMatch = profile.serverUrlPattern.find(l) ?: continue
                            val serverUrl = try {
                                URI(serverMatch.groupValues[1]).toString().trimEnd('/')
                            } catch (e: Exception) {
                                logger.warn("Failed to parse backend URL", e)
                                continue
                            }

                            val apiBaseUrl = serverUrl
                            proc.stopCapture()
                            connected.set(true)
                            timeoutRef.get()?.cancel(false)
                            logger.info("Backend connection established at $apiBaseUrl")
                            SwingUtilities.invokeLater {
                                if (launchGeneration.get() != gen) return@invokeLater
                                connectBrowser(mainPanel, hideableLogs, apiBaseUrl, proc, browserRef)
                            }
                        }
                    } catch (e: java.io.IOException) {
                        if (!connected.get()) {
                            logger.warn("Backend output stream closed before connection was established: ${e.message}")
                            SwingUtilities.invokeLater {
                                if (launchGeneration.get() != gen) return@invokeLater
                                showRecoverableError(mainPanel, hideableLogs, "Backend process terminated unexpectedly.<br/>Check logs for details.", { startBackend() })
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("Error reading backend output", e)
                        SwingUtilities.invokeLater {
                            if (launchGeneration.get() != gen) return@invokeLater
                            showRecoverableError(mainPanel, hideableLogs, "Backend communication error:<br/>${e.message}", { startBackend() })
                        }
                    } finally {
                        reader.closeQuietly()
                    }
                }
                logThread.isDaemon = true
                logThread.start()
            }
        }

        Disposer.register(toolWindow.disposable) {
            timeoutRef.get()?.cancel(false)
            runQuietly { procRef.get()?.destroy() }
        }

        startBackend()
    }

    private fun createLogComponents(): Pair<JTextArea, JComponent> {
        val logArea = JTextArea().apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
        }
        val logScroll = JBScrollPane(logArea)
        val hideableLogs = com.intellij.ui.dsl.builder.panel {
            collapsibleGroup("Backend Logs (Merged Stdout/Stderr)") {
                row {
                    cell(logScroll)
                        .align(com.intellij.ui.dsl.builder.Align.FILL)
                        .resizableColumn()
                }
            }.apply {
                expanded = false
            }
        }.apply {
            border = JBUI.Borders.empty(4)
        }
        return logArea to hideableLogs
    }

    @Suppress("UNUSED_PARAMETER")
    private fun connectBrowser(
        mainPanel: JPanel,
        hideableLogs: JComponent,
        apiBaseUrl: String,
        proc: BackendProcess,
        browserRef: AtomicReference<JBCefBrowser?>
    ) {
        try {
            val client = JBCefApp.getInstance().createClient()
            val browser = JBCefBrowser.createBuilder()
                .setClient(client)
                .build()
            browserRef.set(browser)

            try {
                DragDropHandler.install(project, browser, logger)
            } catch (e: Exception) {
                logger.warn("Failed to set up drag and drop", e)
            }

            mainPanel.removeAll()
            mainPanel.add(browser.component, BorderLayout.CENTER)
            mainPanel.add(hideableLogs, BorderLayout.SOUTH)
            mainPanel.revalidate()
            mainPanel.repaint()

            val session = IdeBridge.createSession(project)
            val uiUrl = buildCustomUiUrl(session.baseUrl, session.token, apiBaseUrl)
            browser.loadURL(uiUrl)

            Disposer.register(toolWindow.disposable) {
                IdeBridge.removeSession(session.sessionId)
            }

            try {
                val filesTracker = OpenFilesTracker(project, session.sessionId)
                filesTracker.install()
                Disposer.register(browser, filesTracker)
            } catch (e: Exception) {
                logger.warn("Failed to install OpenFilesTracker", e)
            }
        } catch (e: Exception) {
            logger.error("Failed to create browser component", e)
            showError(mainPanel, hideableLogs, "Failed to create browser:<br/>${e.message}")
        }
    }

    private fun showLoading(mainPanel: JPanel, hideableLogs: JComponent) {
        mainPanel.removeAll()
        mainPanel.add(JPanel(BorderLayout()).apply {
            add(JLabel("<html><center>Starting agent backend...</center></html>"), BorderLayout.CENTER)
        }, BorderLayout.CENTER)
        mainPanel.add(hideableLogs, BorderLayout.SOUTH)
        mainPanel.revalidate()
        mainPanel.repaint()
    }

    private fun showRecoverableError(
        mainPanel: JPanel,
        hideableLogs: JComponent,
        message: String,
        retryAction: () -> Unit
    ) {
        mainPanel.removeAll()
        mainPanel.add(JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(16)
            add(createSelectableHtml(message), BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.CENTER, 8, 0)).apply {
                add(JButton("Retry").apply {
                    addActionListener { retryAction() }
                })
                add(JButton("Open Settings").apply {
                    addActionListener {
                        ShowSettingsUtil.getInstance().showSettingsDialog(project, AgentellIJConfigurable::class.java)
                    }
                })
            }, BorderLayout.SOUTH)
        }, BorderLayout.CENTER)
        mainPanel.add(hideableLogs, BorderLayout.SOUTH)
        mainPanel.revalidate()
        mainPanel.repaint()
    }

    private fun createSelectableHtml(message: String): JEditorPane {
        return JEditorPane("text/html", "<html><center>$message</center></html>").apply {
            putClientProperty("JEditorPane.honorDisplayProperties", true)
            font = UIManager.getFont("Label.font")
            foreground = UIManager.getColor("Label.foreground")
            isEditable = false
            isOpaque = false
        }
    }

    private fun showError(mainPanel: JPanel, hideableLogs: JComponent, message: String) {
        mainPanel.removeAll()
        mainPanel.add(JPanel(BorderLayout()).apply {
            add(createSelectableHtml(message), BorderLayout.CENTER)
        }, BorderLayout.CENTER)
        mainPanel.add(hideableLogs, BorderLayout.SOUTH)
        mainPanel.revalidate()
        mainPanel.repaint()
    }

    private fun pluginVersion(): String {
        return javaClass.`package`?.implementationVersion ?: java.time.LocalDate.now().toString()
    }

    private fun buildCustomUiUrl(bridgeBaseUrl: String, token: String, opencodeApiUrl: String): String {
        val bridgeOrigin = bridgeBaseUrl.substringBefore("/idebridge")
        return buildString {
            append("$bridgeOrigin/ui/index.html")
            append("?opencodeApi=")
            append(URLEncoder.encode(opencodeApiUrl, StandardCharsets.UTF_8))
            append("&ideBridge=")
            append(URLEncoder.encode(bridgeBaseUrl, StandardCharsets.UTF_8))
            append("&ideBridgeToken=")
            append(URLEncoder.encode(token, StandardCharsets.UTF_8))
            append("&v=")
            append(URLEncoder.encode(pluginVersion(), StandardCharsets.UTF_8))
        }
    }
}
