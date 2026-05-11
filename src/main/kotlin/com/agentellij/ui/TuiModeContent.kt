package com.agentellij.ui

import com.agentellij.backend.BackendLauncher
import com.agentellij.util.resolveAbsolutePath
import com.agentellij.util.runQuietly
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.terminal.ui.TerminalWidget
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
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

class TuiModeContent(
    private val project: Project,
    private val toolWindow: ToolWindow,
    private val parentDisposable: Disposable = toolWindow.disposable
) {
    companion object {
        private val logger = Logger.getInstance(TuiModeContent::class.java)
        private val widgets = Collections.synchronizedMap(WeakHashMap<Project, TerminalWidget>())
        private const val CSI_U_ESCAPE = "\u001B[27u"
        private const val CSI_U_SHIFT_ENTER = "\u001B[13;2u"

        fun getWidget(project: Project): TerminalWidget? = widgets[project]
    }

    fun install() {
        val mainPanel = JPanel(BorderLayout())
        val content = toolWindow.contentManager.factory.createContent(mainPanel, "", false)
        toolWindow.contentManager.addContent(content)

        val baseDir = project.basePath ?: System.getProperty("user.dir")
        val launchCommand = try {
            BackendLauncher.buildLaunchCommand(BackendLauncher.MODE_TUI)
        } catch (e: Exception) {
            logger.warn("Failed to build TUI launch command", e)
            showError(mainPanel, "Failed to build TUI launch command:<br/>${e.message}")
            return
        }

        val binary = launchCommand.firstOrNull()
        if (binary.isNullOrBlank()) {
            showError(mainPanel, "Agent binary is not configured.")
            return
        }

        val resolvedBinary = resolveAbsolutePath(binary)
        if (!isUsableBinary(binary, resolvedBinary)) {
            showError(
                mainPanel,
                "Agent binary not found:<br/><code>${escapeHtml(binary)}</code><br/><br/>Install it or configure an absolute path in settings."
            )
            return
        }

        val terminalWidget = try {
            val runner = TerminalToolWindowManager.getInstance(project).terminalRunner
            val options = ShellStartupOptions.Builder()
                .workingDirectory(baseDir)
                .build()
            runner.startShellTerminalWidget(parentDisposable, options, true)
        } catch (e: Exception) {
            logger.warn("Failed to create terminal widget", e)
            showError(mainPanel, "Failed to create terminal widget:<br/>${e.message}")
            return
        }

        widgets[project] = terminalWidget
        mainPanel.add(terminalWidget.component, BorderLayout.CENTER)
        mainPanel.revalidate()
        mainPanel.repaint()

        // Block xterm "all motion" mouse tracking (DECSET 1003h) from reaching the
        // TUI: BubbleTea-based TUIs interpret reported mouse moves as cursor moves,
        // so the embedded prompt cursor follows the mouse without this filter.
        installMouseMotionFilter(terminalWidget.component)

        installTerminalKeyForwarder(terminalWidget)

        Disposer.register(parentDisposable) {
            widgets.remove(project)
            destroyTerminalProcess(terminalWidget)
            runQuietly { (terminalWidget as? Disposable)?.let(Disposer::dispose) }
        }

        try {
            executeCommand(terminalWidget, launchCommand.toShellCommand())
        } catch (e: Exception) {
            logger.warn("Failed to start TUI command", e)
            widgets.remove(project)
            destroyTerminalProcess(terminalWidget)
            runQuietly { (terminalWidget as? Disposable)?.let(Disposer::dispose) }
            showError(mainPanel, "Failed to start TUI command:<br/>${e.message}")
        }
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
        runQuietly {
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
                runQuietly { connector.write(sequence) }
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

    private fun isUsableBinary(binary: String, resolvedBinary: String): Boolean {
        val resolvedFile = File(resolvedBinary)
        if (resolvedFile.isAbsolute) return resolvedFile.exists() && resolvedFile.canExecute()

        val rawFile = File(binary)
        return rawFile.isAbsolute && rawFile.exists() && rawFile.canExecute()
    }

    private fun List<String>.toShellCommand(): String = joinToString(" ") { it.shellQuote() }

    private fun String.shellQuote(): String {
        val windows = System.getProperty("os.name").lowercase().contains("win")
        return if (windows) {
            if (isEmpty()) "\"\"" else if (any { it.isWhitespace() || it == '"' }) {
                "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
            } else {
                this
            }
        } else {
            if (isEmpty()) "''" else if (any { it.isWhitespace() || it in "'\\\"$`()[]{}*?&;|<>" }) {
                "'" + replace("'", "'\"'\"'") + "'"
            } else {
                this
            }
        }
    }

    private fun escapeHtml(value: String): String = buildString(value.length) {
        for (ch in value) {
            append(
                when (ch) {
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '&' -> "&amp;"
                    '"' -> "&quot;"
                    else -> ch
                }
            )
        }
    }

    private fun showError(mainPanel: JPanel, message: String) {
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
                runQuietly { wrapMotionListenersInTree(root) }
                if (ticks.incrementAndGet() >= maxTicks) timer.stop()
            }
            timer.isRepeats = true
            timer.start()
            Disposer.register(parentDisposable) { runQuietly { timer.stop() } }
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
