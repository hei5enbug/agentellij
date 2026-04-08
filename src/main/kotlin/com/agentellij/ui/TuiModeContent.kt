package com.agentellij.ui

import com.agentellij.backend.BackendLauncher
import com.agentellij.util.resolveAbsolutePath
import com.agentellij.util.runQuietly
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.terminal.ui.TerminalWidget
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.awt.BorderLayout
import java.awt.event.KeyEvent
import java.io.File
import java.util.Collections
import java.util.WeakHashMap
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke

class TuiModeContent(
    private val project: Project,
    private val toolWindow: ToolWindow
) {
    companion object {
        private val logger = Logger.getInstance(TuiModeContent::class.java)
        private val widgets = Collections.synchronizedMap(WeakHashMap<Project, TerminalWidget>())

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
            runner.startShellTerminalWidget(toolWindow.disposable, options, true)
        } catch (e: Exception) {
            logger.warn("Failed to create terminal widget", e)
            showError(mainPanel, "Failed to create terminal widget:<br/>${e.message}")
            return
        }

        widgets[project] = terminalWidget
        mainPanel.add(terminalWidget.component, BorderLayout.CENTER)
        mainPanel.revalidate()
        mainPanel.repaint()

        object : DumbAwareAction() {
            override fun actionPerformed(e: AnActionEvent) {
                terminalWidget.ttyConnector?.let { connector ->
                    runQuietly { connector.write("\u001B") }
                }
            }
        }.registerCustomShortcutSet(
            CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)),
            terminalWidget.component,
            toolWindow.disposable
        )

        Disposer.register(toolWindow.disposable) {
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
}
