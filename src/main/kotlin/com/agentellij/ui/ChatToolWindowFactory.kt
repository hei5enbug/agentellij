package com.agentellij.ui

import com.agentellij.settings.AgentellIJSettings
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import java.util.concurrent.atomic.AtomicReference

class ChatToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val modeDisposableRef = AtomicReference<Disposable?>(null)

        fun installMode(mode: String) {
            modeDisposableRef.getAndSet(null)?.let { Disposer.dispose(it) }

            toolWindow.contentManager.contents.toList().forEach {
                toolWindow.contentManager.removeContent(it, true)
            }

            val modeDisposable = Disposer.newDisposable("agentellij-mode-$mode")
            Disposer.register(toolWindow.disposable, modeDisposable)
            modeDisposableRef.set(modeDisposable)

            when (mode) {
                "tui" -> TuiModeContent(project, toolWindow, modeDisposable).install()
                else  -> GuiModeContent(project, toolWindow, modeDisposable).install()
            }
        }

        val switchAction = object : DumbAwareAction() {
            override fun getActionUpdateThread() = ActionUpdateThread.BGT

            override fun actionPerformed(e: AnActionEvent) {
                val settings = AgentellIJSettings.getInstance()
                val newMode = if (settings.getMode() == "tui") "gui" else "tui"
                settings.state.mode = newMode
                installMode(newMode)
            }

            override fun update(e: AnActionEvent) {
                if (AgentellIJSettings.getInstance().getMode() == "tui") {
                    e.presentation.text = "Switch to GUI Mode"
                    e.presentation.description = "Switch to the browser-based chat interface"
                    e.presentation.icon = AllIcons.General.LayoutPreviewOnly
                } else {
                    e.presentation.text = "Switch to TUI Mode"
                    e.presentation.description = "Switch to the terminal-based TUI interface"
                    e.presentation.icon = AllIcons.Debugger.Console
                }
            }
        }

        toolWindow.setTitleActions(listOf(switchAction))

        installMode(AgentellIJSettings.getInstance().getMode())
    }
}
