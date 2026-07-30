package com.agentellij.platform.surface

import com.agentellij.core.bridge.BridgeUiUrl
import com.agentellij.core.launch.StartupAttempt
import com.agentellij.core.launch.BinaryUsability
import com.agentellij.core.util.closeQuietly
import com.agentellij.core.util.runQuietly
import com.agentellij.platform.bridge.IdeBridge
import com.agentellij.platform.config.AgentellIJConfigurable
import com.agentellij.platform.env.IdeLoggerDiagnostics
import com.agentellij.platform.env.resolveAbsolutePath
import com.agentellij.platform.ide.DragDropHandler
import com.agentellij.platform.ide.OpenFilesTracker
import com.agentellij.platform.process.BackendLauncher
import com.agentellij.platform.process.BackendProcess
import com.agentellij.core.agent.AgentProfile
import com.agentellij.platform.toolwindow.AgentellIJWiring
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
import java.nio.charset.StandardCharsets
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.UIManager

private typealias Attempt = StartupAttempt<java.util.concurrent.ScheduledFuture<*>, com.agentellij.platform.process.BackendProcess>

class GuiModeContent(
    private val project: Project,
    private val toolWindow: ToolWindow,
    private val parentDisposable: Disposable = toolWindow.disposable,
    private val profile: AgentProfile
) {
    companion object {
        private val logger = Logger.getInstance(GuiModeContent::class.java)
        private const val CONNECT_TIMEOUT_MILLIS = 300_000L
        private val diagnostics = IdeLoggerDiagnostics(logger)
    }



    /**
     * The attempt currently on screen.
     *
     * Each attempt owns its own timer and process, so a callback arriving late from an
     * abandoned attempt can only reach what that attempt owned. Sharing those fields
     * across attempts let a stale failure cancel a live attempt's timer.
     */
    private val attempt = AtomicReference(StartupAttempt<ScheduledFuture<*>, BackendProcess>())
    private val browser = AtomicReference<JBCefBrowser?>(null)

    fun install() {
        val mainPanel = JPanel(BorderLayout())
        toolWindow.contentManager.addContent(
            toolWindow.contentManager.factory.createContent(mainPanel, "", false)
        )

        if (!JBCefApp.isSupported()) {
            mainPanel.add(unsupportedRuntimeNotice(), BorderLayout.CENTER)
            return
        }

        Disposer.register(parentDisposable) {
            release(attempt.get())
            browser.getAndSet(null)?.let { runQuietly(diagnostics, "dispose the embedded browser") { Disposer.dispose(it) } }
        }
        startBackend(mainPanel)
    }

    private fun unsupportedRuntimeNotice(): JLabel = JLabel(
        "<html><center>JCEF is not supported on this platform.<br/>" +
            "Please use a JetBrains Runtime that includes JCEF.</center></html>"
    )

    private fun release(finished: StartupAttempt<ScheduledFuture<*>, BackendProcess>) {
        val (timer, backend) = finished.release()
        timer?.cancel(false)
        runQuietly(diagnostics, "stop the agent process") { backend?.destroy() }
    }

    private fun startBackend(mainPanel: JPanel) {
        // Publish the new attempt before releasing the old one, so nothing in between
        // can attach a resource to an attempt that is already finished.
        val started = StartupAttempt<ScheduledFuture<*>, BackendProcess>()
        release(attempt.getAndSet(started))
        browser.getAndSet(null)?.let { runQuietly(diagnostics, "dispose the embedded browser") { Disposer.dispose(it) } }

        showLoading(mainPanel)
        scheduleTimeout(mainPanel, started)

        AppExecutorUtil.getAppExecutorService().execute { launch(mainPanel, started) }
    }

    private fun scheduleTimeout(mainPanel: JPanel, current: Attempt) {
        val handle = AppExecutorUtil.getAppScheduledExecutorService().schedule({
            if (!current.markTimedOut()) return@schedule
            logger.warn("Backend connection timeout after ${CONNECT_TIMEOUT_MILLIS}ms")
            onEdt(current) {
                showRecoverableError(mainPanel, "Backend connection timeout.<br/>Check logs for details.") {
                    startBackend(mainPanel)
                }
            }
            runQuietly(diagnostics, "stop a timed out agent process") { current.processHandle()?.destroy() }
        }, CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)

        if (!current.attachTimer(handle)) handle.cancel(false)
    }

    /** Runs the action on the UI thread, but only while this attempt is still on screen. */
    private fun onEdt(current: Attempt, action: () -> Unit) = SwingUtilities.invokeLater {
        if (attempt.get() === current) action()
    }

    private fun abandon(mainPanel: JPanel, current: Attempt, message: String) {
        current.timerHandle()?.cancel(false)
        onEdt(current) { showRecoverableError(mainPanel, message) { startBackend(mainPanel) } }
    }

    private fun launch(mainPanel: JPanel, current: Attempt) {
        if (!current.isPending()) return

        val settingsPath = AgentellIJWiring.binaryPathFor(profile)
        val customArgs = AgentellIJWiring.customArgs()

        val launchCommand = try {
            BackendLauncher.buildLaunchCommand(profile, BackendLauncher.MODE_GUI, settingsPath, customArgs)
        } catch (e: Exception) {
            logger.warn("Failed to build GUI launch command", e)
            abandon(mainPanel, current, "Failed to build backend launch command:<br/>${e.message}")
            return
        }

        val binary = launchCommand.firstOrNull()
        if (binary.isNullOrBlank()) {
            abandon(mainPanel, current, "Agent binary is not configured.")
            return
        }

        if (!isUsableBinary(binary, resolveAbsolutePath(binary))) {
            current.timerHandle()?.cancel(false)
            onEdt(current) {
                AgentCliInstallPanel.showMissingCli(
                    project = project,
                    mainPanel = mainPanel,
                    profile = profile,
                    binary = binary,
                    retryAction = { startBackend(mainPanel) }
                )
            }
            return
        }

        val started = try {
            BackendLauncher.launchBackend(project, profile, settingsPath, customArgs)
        } catch (e: Exception) {
            logger.error("Failed to launch backend", e)
            abandon(
                mainPanel,
                current,
                "Failed to start backend:<br/>${e.message}<br/><br/>" +
                    "Is the agent binary installed and on your PATH?"
            )
            return
        }

        // The attempt may have been replaced or disposed while the launch was running.
        if (!current.attachProcess(started) || attempt.get() !== current) {
            runQuietly(diagnostics, "stop an abandoned agent process") { started.destroy() }
            return
        }
        watchForServerUrl(mainPanel, current, started)
    }

    /**
     * Reads the agent's output until it announces the address its web UI is on.
     *
     * The agent prints that line once, early, so the reader runs on its own thread and
     * stops looking as soon as the browser is attached.
     */
    private fun watchForServerUrl(mainPanel: JPanel, current: Attempt, backend: BackendProcess) {
        val reader = BufferedReader(InputStreamReader(backend.inputStream, StandardCharsets.UTF_8))
        Thread {
            try {
                reader.lineSequence().forEach { line ->
                    if (!current.isPending()) return@forEach
                    val serverUrl = parseServerUrl(line.trim()) ?: return@forEach
                    if (!current.markConnected()) return@forEach

                    backend.stopCapture()
                    current.timerHandle()?.cancel(false)
                    logger.info("Backend connection established at $serverUrl")
                    onEdt(current) { connectBrowser(mainPanel, serverUrl, backend, browser) }
                }
            } catch (e: java.io.IOException) {
                if (current.isPending()) {
                    logger.warn("Backend output stream closed before connection was established: ${e.message}")
                    onEdt(current) {
                        showRecoverableError(
                            mainPanel,
                            "Backend process terminated unexpectedly.<br/>Check logs for details."
                        ) { startBackend(mainPanel) }
                    }
                }
            } catch (e: Exception) {
                logger.error("Error reading backend output", e)
                onEdt(current) {
                    showRecoverableError(mainPanel, "Backend communication error:<br/>${e.message}") {
                        startBackend(mainPanel)
                    }
                }
            } finally {
                reader.closeQuietly(diagnostics, "the agent output reader")
            }
        }.apply { isDaemon = true }.start()
    }

    private fun parseServerUrl(line: String): String? {
        val match = profile.serverUrlPattern.find(line) ?: return null
        return try {
            URI(match.groupValues[1]).toString().trimEnd('/')
        } catch (e: Exception) {
            logger.warn("Failed to parse backend URL", e)
            null
        }
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
            if (event.isConsumed) return@KeyEventDispatcher true
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
        runQuietly(diagnostics, "forward escape to the web client") {
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

    private fun isUsableBinary(binary: String, resolvedBinary: String): Boolean =
        BinaryUsability.isUsable(
            binary = binary,
            resolvedBinary = resolvedBinary,
            isAbsolute = { java.io.File(it).isAbsolute },
            exists = { java.io.File(it).exists() },
            canExecute = { java.io.File(it).canExecute() }
        )

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

    private fun buildCustomUiUrl(bridgeBaseUrl: String, token: String, opencodeApiUrl: String): String =
        BridgeUiUrl.build(
            bridgeBaseUrl = bridgeBaseUrl,
            token = token,
            agentApiUrl = opencodeApiUrl,
            agentName = profile.displayName,
            pluginVersion = pluginVersion()
        )
}
