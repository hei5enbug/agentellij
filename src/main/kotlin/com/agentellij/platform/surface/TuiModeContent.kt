package com.agentellij.platform.surface

import com.agentellij.platform.env.IdeLoggerDiagnostics
import com.agentellij.core.launch.TerminalShellCommand
import com.agentellij.core.launch.TuiLaunchPlan
import com.agentellij.core.util.runQuietly
import com.agentellij.platform.install.escapeHtml
import com.agentellij.platform.process.BackendLauncher
import com.agentellij.platform.process.TuiCompletionRuntime
import com.agentellij.core.agent.AgentProfile
import com.agentellij.core.agent.AgentCatalog
import com.agentellij.platform.toolwindow.AgentellIJWiring
import com.agentellij.platform.env.resolveAbsolutePath
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionListener
import java.io.File
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

class TuiModeContent(
    private val project: Project,
    private val toolWindow: ToolWindow,
    private val parentDisposable: Disposable = toolWindow.disposable,
    private val profile: AgentProfile
) {
    companion object {
        private val logger = Logger.getInstance(TuiModeContent::class.java)
        private val widgets = Collections.synchronizedMap(WeakHashMap<Project, TerminalWidget>())
        private const val CSI_U_ESCAPE = "\u001B[27u"
        private const val CSI_U_SHIFT_ENTER = "\u001B[13;2u"

        fun getWidget(project: Project): TerminalWidget? = widgets[project]
    }

    private val disposed = AtomicBoolean(false)
    private val diagnostics = IdeLoggerDiagnostics(logger)
    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    init {
        Disposer.register(parentDisposable) { disposed.set(true) }
    }

    fun install() {
        val mainPanel = JPanel(BorderLayout())
        val content = toolWindow.contentManager.factory.createContent(mainPanel, "", false)
        toolWindow.contentManager.addContent(content)

        fun retryInstall() {
            toolWindow.contentManager.removeContent(content, true)
            install()
        }

        showLoading(mainPanel, "Preparing ${escapeHtml(profile.displayName)}...")
        AppExecutorUtil.getAppExecutorService().execute {
            try {
                val baseDir = project.basePath ?: System.getProperty("user.dir")
                val plan = BackendLauncher.buildTuiLaunchPlan(
                    profile = profile,
                    settingsPath = AgentellIJWiring.binaryPathFor(profile),
                    customArgs = AgentellIJWiring.customArgs()
                )
                if (!plan.installed) {
                    showOnEdt {
                        AgentCliInstallPanel.showMissingCli(
                            project = project,
                            mainPanel = mainPanel,
                            profile = profile,
                            binary = profile.defaultBinary,
                            retryAction = ::retryInstall
                        )
                    }
                    return@execute
                }

                val completionRuntime = createCompletionRuntime(plan)
                if (completionRuntime != null) {
                    try {
                        Disposer.register(parentDisposable, completionRuntime)
                    } catch (failure: Throwable) {
                        completionRuntime.dispose()
                        throw failure
                    }
                }
                showOnEdt { startTerminal(mainPanel, baseDir, plan, completionRuntime) }
            } catch (t: Throwable) {
                logger.warn("Failed to prepare TUI backend", t)
                showOnEdt {
                    showError(
                        mainPanel,
                        "Failed to prepare ${escapeHtml(profile.displayName)}:<br/>${escapeHtml(t.message ?: t.javaClass.name)}"
                    )
                }
            }
        }
    }

    /**
     * Runs [block] on the EDT, but only if this mode's content is still alive.
     * Using [ModalityState.any] guarantees the update is delivered even while a modal
     * dialog is open, and the disposal guard prevents touching a tool window that was
     * torn down (for example, after a mode switch or plugin reload) — which previously
     * could leave the panel stuck on "Preparing…".
     */
    private fun showOnEdt(block: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(
            {
                if (!disposed.get() && !project.isDisposed) {
                    block()
                }
            },
            ModalityState.any()
        )
    }

    private fun wrapTerminalCommand(command: List<String>): List<String> = TerminalShellCommand.wrap(
        command = command,
        isWindows = isWindows,
        env = { System.getenv(it) },
        isExecutable = { File(it).canExecute() },
        fileExists = { File(it).exists() }
    )

    private fun renderTerminalCommand(command: List<String>): String =
        TerminalShellCommand.renderInner(command, isWindows)
    private fun createCompletionRuntime(activePlan: TuiLaunchPlan): TuiCompletionRuntime? = try {
        val binaries = AgentCatalog.allProfiles()
            .asSequence()
            .filterNot { it.usesDefaultShell }
            .mapNotNull { candidate ->
                val plan = if (candidate.id == profile.id) {
                    activePlan
                } else {
                    BackendLauncher.buildTuiLaunchPlan(
                        profile = candidate,
                        settingsPath = AgentellIJWiring.binaryPathFor(candidate),
                        customArgs = "",
                        agentellijBin = null
                    )
                }
                if (!plan.installed) return@mapNotNull null
                val binary = plan.command.firstOrNull() ?: return@mapNotNull null
                val absolute = resolveAbsolutePath(binary)
                if (!File(absolute).exists()) return@mapNotNull null
                candidate.id to absolute
            }
            .toMap()
        TuiCompletionRuntime.create(
            project = project,
            agentBinaries = binaries,
            inheritedPath = System.getenv("PATH"),
            inheritedOpenCodeConfig = System.getenv("OPENCODE_CONFIG_CONTENT")
        )
    } catch (e: Exception) {
        logger.warn("Failed to prepare agent completion notifications", e)
        null
    }

    private fun startTerminal(
        mainPanel: JPanel,
        baseDir: String,
        plan: TuiLaunchPlan,
        completionRuntime: TuiCompletionRuntime?
    ) {
        val runner = TerminalToolWindowManager.getInstance(project).terminalRunner
        val environment = completionRuntime?.environment.orEmpty()

        if (plan.usesDefaultShell) {
            val terminalWidget = try {
                val options = ShellStartupOptions.Builder()
                    .workingDirectory(baseDir)
                    .envVariables(environment)
                    .build()
                runner.startShellTerminalWidget(parentDisposable, options, true)
            } catch (e: Exception) {
                logger.warn("Failed to create default-shell terminal widget", e)
                showError(mainPanel, "Failed to create terminal widget:<br/>${e.message}")
                return
            }
            attachWidget(mainPanel, terminalWidget)
            completionRuntime?.shellActivationCommand?.let { command ->
                try {
                    executeCommand(terminalWidget, command)
                } catch (e: Exception) {
                    logger.warn("Failed to activate agent completion wrappers in the terminal shell", e)
                }
            }
            return
        }

        val command = completionRuntime?.wrapCommand(profile.id, plan.command) ?: plan.command
        val (terminalWidget, fallbackCommand) = try {
            val options = ShellStartupOptions.Builder()
                .workingDirectory(baseDir)
                .shellCommand(wrapTerminalCommand(command))
                .envVariables(environment)
                .build()
            runner.startShellTerminalWidget(parentDisposable, options, true) to null
        } catch (e: Exception) {
            logger.warn("Failed to create terminal widget with startup shell command", e)
            try {
                val options = ShellStartupOptions.Builder()
                    .workingDirectory(baseDir)
                    .envVariables(environment)
                    .build()
                runner.startShellTerminalWidget(parentDisposable, options, true) to renderTerminalCommand(command)
            } catch (fallbackError: Exception) {
                logger.warn("Failed to create terminal widget", fallbackError)
                showError(mainPanel, "Failed to create terminal widget:<br/>${fallbackError.message}")
                return
            }
        }

        attachWidget(mainPanel, terminalWidget)

        if (fallbackCommand != null) {
            try {
                executeCommand(terminalWidget, fallbackCommand)
            } catch (e: Exception) {
                logger.warn("Failed to start TUI command", e)
                widgets.remove(project)
                destroyTerminalProcess(terminalWidget)
                runQuietly(diagnostics, "dispose the terminal widget") { (terminalWidget as? Disposable)?.let(Disposer::dispose) }
                showError(mainPanel, "Failed to start TUI command:<br/>${e.message}")
            }
        }
    }

    private fun attachWidget(mainPanel: JPanel, terminalWidget: TerminalWidget) {
        widgets[project] = terminalWidget
        mainPanel.removeAll()
        mainPanel.add(terminalWidget.component, BorderLayout.CENTER)
        mainPanel.revalidate()
        mainPanel.repaint()
        Disposer.register(parentDisposable) {
            widgets.remove(project)
            destroyTerminalProcess(terminalWidget)
            runQuietly(diagnostics, "dispose the terminal widget") { (terminalWidget as? Disposable)?.let(Disposer::dispose) }
        }

        // Block xterm "all motion" mouse tracking (DECSET 1003h): a BubbleTea TUI would otherwise
        // interpret reported mouse moves as cursor moves, dragging the embedded prompt cursor.
        installMouseMotionFilter(terminalWidget.component)
        installTerminalKeyForwarder(terminalWidget)
    }

    private fun executeCommand(widget: TerminalWidget, command: String) {
        val shellWidget = widget as? ShellTerminalWidget
        if (shellWidget != null) {
            shellWidget.executeCommand(command)
        } else {
            widget.sendCommandToExecute(command)
        }
    }

    private fun destroyTerminalProcess(widget: TerminalWidget) {
        runQuietly(diagnostics, "stop the terminal process") {
            val process = widget.ttyConnector?.let { ShellTerminalWidget.getProcessTtyConnector(it)?.process }
            if (process != null) {
                process.destroy()
            } else {
                widget.ttyConnector?.write("\u0003")
            }
        }
    }

    private fun installTerminalKeyForwarder(terminalWidget: TerminalWidget) {
        val suppressNextEnterTyped = AtomicBoolean(false)
        val dispatcher = KeyEventDispatcher { event ->
            if (!isTerminalEvent(terminalWidget.component)) return@KeyEventDispatcher false

            if (event.id == KeyEvent.KEY_TYPED && event.keyChar == '\n' && suppressNextEnterTyped.compareAndSet(true, false)) {
                event.consume()
                return@KeyEventDispatcher true
            }

            if (event.id != KeyEvent.KEY_PRESSED) return@KeyEventDispatcher false
            if (event.isConsumed) return@KeyEventDispatcher true

            val sequence = when {
                event.keyCode == KeyEvent.VK_ESCAPE -> CSI_U_ESCAPE
                event.keyCode == KeyEvent.VK_ENTER && event.isShiftDown -> {
                    suppressNextEnterTyped.set(true)
                    CSI_U_SHIFT_ENTER
                }
                else -> return@KeyEventDispatcher false
            }

            terminalWidget.ttyConnector?.let { connector ->
                runQuietly(diagnostics, "forward a key to the terminal") { connector.write(sequence) }
                event.consume()
                return@KeyEventDispatcher true
            }

            false
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher)
        Disposer.register(parentDisposable) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher)
        }
    }

    private fun isTerminalEvent(terminalComponent: Component): Boolean {
        if (!toolWindow.isVisible) return false

        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return false
        return SwingUtilities.isDescendingFrom(focusOwner, terminalComponent)
    }

    private fun showLoading(mainPanel: JPanel, message: String) = showCenteredMessage(mainPanel, message)

    private fun showError(mainPanel: JPanel, message: String) = showCenteredMessage(mainPanel, message)

    private fun showCenteredMessage(mainPanel: JPanel, message: String) {
        mainPanel.removeAll()
        mainPanel.add(JPanel(BorderLayout()).apply {
            add(JLabel("<html><center>$message</center></html>"), BorderLayout.CENTER)
        }, BorderLayout.CENTER)
        mainPanel.revalidate()
        mainPanel.repaint()
    }

    private val wrappedMotionListeners: MutableSet<MouseMotionListener> =
        Collections.newSetFromMap(WeakHashMap())

    private fun installMouseMotionFilter(root: Component) {
        SwingUtilities.invokeLater {
            val ticks = AtomicInteger(0)
            val maxTicks = 15
            lateinit var timer: Timer
            timer = Timer(200) {
                runQuietly(diagnostics, "filter terminal mouse movement") { wrapMotionListenersInTree(root) }
                if (ticks.incrementAndGet() >= maxTicks) timer.stop()
            }
            timer.isRepeats = true
            timer.start()
            Disposer.register(parentDisposable) { runQuietly(diagnostics, "stop the mouse filter timer") { timer.stop() } }
        }
    }

    private fun wrapMotionListenersInTree(root: Component) {
        val queue = ArrayDeque<Component>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            if (isTerminalPanelLike(c)) wrapMouseMotionListeners(c)
            if (c is Container) c.components.forEach { queue.add(it) }
        }
    }

    private fun isTerminalPanelLike(c: Component): Boolean {
        val name = c.javaClass.name
        return name.endsWith("TerminalPanel") || name.endsWith("JBTerminalPanel")
    }

    private fun wrapMouseMotionListeners(panel: Component) {
        val unwrapped = panel.mouseMotionListeners.filter { it !in wrappedMotionListeners }
        if (unwrapped.isEmpty()) return
        for (original in unwrapped) {
            panel.removeMouseMotionListener(original)
            val wrapper = MouseMotionFilter(original)
            panel.addMouseMotionListener(wrapper)
            wrappedMotionListeners.add(wrapper)
        }
    }

    private class MouseMotionFilter(private val delegate: MouseMotionListener) : MouseMotionListener {
        override fun mouseMoved(e: MouseEvent) {
            // dropped: prevents JediTerm from forwarding mouse-move events to the PTY
        }

        override fun mouseDragged(e: MouseEvent) {
            delegate.mouseDragged(e)
        }
    }
}
