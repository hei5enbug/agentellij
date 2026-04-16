package com.agentellij.ui

import com.agentellij.backend.BackendLauncher
import com.agentellij.backend.BackendProcess
import com.agentellij.backend.AgentProfileResolver
import com.agentellij.bridge.IdeBridge
import com.agentellij.context.DragDropHandler
import com.agentellij.settings.AgentellIJConfigurable
import com.agentellij.util.closeQuietly
import com.agentellij.util.runQuietly
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
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
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.UIManager

class GuiModeContent(
    private val project: Project,
    private val toolWindow: ToolWindow,
    private val parentDisposable: Disposable = toolWindow.disposable
) {
    companion object {
        private val logger = Logger.getInstance(GuiModeContent::class.java)
    }

    private val profile = AgentProfileResolver.resolve()

    fun install() {
        val mainPanel = JPanel(BorderLayout())
        val content = toolWindow.contentManager.factory.createContent(mainPanel, "", false)
        toolWindow.contentManager.addContent(content)

        if (!JBCefApp.isSupported()) {
            mainPanel.add(JLabel("<html><center>JCEF is not supported on this platform.<br/>Please use a JetBrains Runtime that includes JCEF.</center></html>"), BorderLayout.CENTER)
            return
        }

        val procRef = AtomicReference<BackendProcess?>(null)
        val browserRef = AtomicReference<JBCefBrowser?>(null)
        val connected = AtomicBoolean(false)
        val launchGeneration = AtomicInteger(0)
        val timeoutRef = AtomicReference<ScheduledFuture<*>?>(null)

        val timeoutMs = 300_000L

        fun startBackend() {
            val gen = launchGeneration.incrementAndGet()

            timeoutRef.getAndSet(null)?.cancel(false)
            runQuietly { procRef.getAndSet(null)?.destroy() }
            browserRef.getAndSet(null)?.let { runQuietly { Disposer.dispose(it) } }
            connected.set(false)

            showLoading(mainPanel)

            val timeout = AppExecutorUtil.getAppScheduledExecutorService().schedule({
                if (connected.get() || launchGeneration.get() != gen) return@schedule
                logger.warn("Backend connection timeout after ${timeoutMs}ms")
                SwingUtilities.invokeLater {
                    if (launchGeneration.get() != gen) return@invokeLater
                    showRecoverableError(mainPanel, "Backend connection timeout.<br/>Check logs for details.", { startBackend() })
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
                        showRecoverableError(mainPanel, "Failed to start backend:<br/>${e.message}<br/><br/>Is the agent binary installed and on your PATH?", { startBackend() })
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
                                connectBrowser(mainPanel, apiBaseUrl, proc, browserRef)
                            }
                        }
                    } catch (e: java.io.IOException) {
                        if (!connected.get()) {
                            logger.warn("Backend output stream closed before connection was established: ${e.message}")
                            SwingUtilities.invokeLater {
                                if (launchGeneration.get() != gen) return@invokeLater
                                showRecoverableError(mainPanel, "Backend process terminated unexpectedly.<br/>Check logs for details.", { startBackend() })
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("Error reading backend output", e)
                        SwingUtilities.invokeLater {
                            if (launchGeneration.get() != gen) return@invokeLater
                            showRecoverableError(mainPanel, "Backend communication error:<br/>${e.message}", { startBackend() })
                        }
                    } finally {
                        reader.closeQuietly()
                    }
                }
                logThread.isDaemon = true
                logThread.start()
            }
        }

        Disposer.register(parentDisposable) {
            timeoutRef.get()?.cancel(false)
            runQuietly { procRef.get()?.destroy() }
            browserRef.getAndSet(null)?.let { runQuietly { Disposer.dispose(it) } }
        }

        startBackend()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun connectBrowser(
        mainPanel: JPanel,
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
            mainPanel.revalidate()
            mainPanel.repaint()

            val session = IdeBridge.createSession(project)
            val uiUrl = buildCustomUiUrl(session.baseUrl, session.token, apiBaseUrl)
            browser.loadURL(uiUrl)

            installEscapeHandler(browser)

            Disposer.register(parentDisposable) {
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
            showError(mainPanel, "Failed to create browser:<br/>${e.message}")
        }
    }

    private fun installEscapeHandler(browser: JBCefBrowser) {
        object : DumbAwareAction() {
            override fun actionPerformed(e: AnActionEvent) {
                forwardEscapeToBrowser(browser)
            }
        }.registerCustomShortcutSet(
            CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)),
            browser.component,
            browser
        )

        val escapeDispatcher = KeyEventDispatcher { event ->
            if (event.keyCode != KeyEvent.VK_ESCAPE) return@KeyEventDispatcher false
            if (event.id != KeyEvent.KEY_PRESSED) return@KeyEventDispatcher false
            if (!toolWindow.isVisible) return@KeyEventDispatcher false

            val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
                ?: return@KeyEventDispatcher false
            if (!SwingUtilities.isDescendingFrom(focusOwner, browser.component)) return@KeyEventDispatcher false

            forwardEscapeToBrowser(browser)
            event.consume()
            true
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(escapeDispatcher)
        Disposer.register(browser) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(escapeDispatcher)
        }
    }

    private fun forwardEscapeToBrowser(browser: JBCefBrowser) {
        runQuietly {
            browser.cefBrowser.executeJavaScript(
                "(function(){var t=document.activeElement||document.body;" +
                    "t.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',code:'Escape'," +
                    "keyCode:27,which:27,bubbles:true,cancelable:true}));})()",
                "escape-forward", 0
            )
        }
    }

    private fun showLoading(mainPanel: JPanel) {
        mainPanel.removeAll()
        mainPanel.add(JPanel(BorderLayout()).apply {
            add(JLabel("<html><center>Starting agent backend...</center></html>"), BorderLayout.CENTER)
        }, BorderLayout.CENTER)
        mainPanel.revalidate()
        mainPanel.repaint()
    }

    private fun showRecoverableError(mainPanel: JPanel, message: String, retryAction: () -> Unit) {
        mainPanel.removeAll()
        mainPanel.add(JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(16)
            add(createSelectableHtml(message), BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.CENTER, 8, 0)).apply {
                add(JButton("Retry").apply { addActionListener { retryAction() } })
                add(JButton("Open Settings").apply {
                    addActionListener {
                        ShowSettingsUtil.getInstance().showSettingsDialog(project, AgentellIJConfigurable::class.java)
                    }
                })
            }, BorderLayout.SOUTH)
        }, BorderLayout.CENTER)
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

    private fun showError(mainPanel: JPanel, message: String) {
        mainPanel.removeAll()
        mainPanel.add(JPanel(BorderLayout()).apply {
            add(createSelectableHtml(message), BorderLayout.CENTER)
        }, BorderLayout.CENTER)
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
            append("&agentName=")
            append(URLEncoder.encode(profile.displayName, StandardCharsets.UTF_8))
            append("&v=")
            append(URLEncoder.encode(pluginVersion(), StandardCharsets.UTF_8))
        }
    }
}
