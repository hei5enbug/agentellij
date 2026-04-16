package com.agentellij.ui

import com.agentellij.backend.AgentProfileResolver
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

        val agentSwitchAction = object : DumbAwareAction() {
            override fun getActionUpdateThread() = ActionUpdateThread.BGT

            override fun actionPerformed(e: AnActionEvent) {
                val settings = AgentellIJSettings.getInstance()
                val profiles = AgentProfileResolver.allProfiles()
                val currentProfile = AgentProfileResolver.resolve()
                val currentIndex = profiles.indexOfFirst { it.id == currentProfile.id }
                val nextProfile = profiles[(currentIndex + 1) % profiles.size]

                settings.state.activeAgent = nextProfile.id

                val currentMode = settings.getMode()
                if (currentMode !in nextProfile.supportedModes) {
                    settings.state.mode = nextProfile.supportedModes.first()
                }

                installMode(settings.getMode())
            }

            override fun update(e: AnActionEvent) {
                val profiles = AgentProfileResolver.allProfiles()
                val currentProfile = AgentProfileResolver.resolve()
                val currentIndex = profiles.indexOfFirst { it.id == currentProfile.id }
                val nextProfile = profiles[(currentIndex + 1) % profiles.size]

                e.presentation.text = "Switch to ${nextProfile.displayName}"
                e.presentation.description = "Switch agent from ${currentProfile.displayName} to ${nextProfile.displayName}"
                e.presentation.icon = AllIcons.Actions.SwapPanels
            }
        }

        val modeSwitchAction = object : DumbAwareAction() {
            override fun getActionUpdateThread() = ActionUpdateThread.BGT

            override fun actionPerformed(e: AnActionEvent) {
                val settings = AgentellIJSettings.getInstance()
                val newMode = if (settings.getMode() == "tui") "gui" else "tui"
                settings.state.mode = newMode
                installMode(newMode)
            }

            override fun update(e: AnActionEvent) {
                val profile = AgentProfileResolver.resolve()
                val enabled = profile.supportedModes.size > 1

                e.presentation.isEnabled = enabled
                if (AgentellIJSettings.getInstance().getMode() == "tui") {
                    e.presentation.text = if (enabled) "Switch to GUI Mode" else "GUI Mode Not Available"
                    e.presentation.description = if (enabled) "Switch to the browser-based chat interface" else "${profile.displayName} only supports TUI mode"
                    e.presentation.icon = AllIcons.General.LayoutPreviewOnly
                } else {
                    e.presentation.text = "Switch to TUI Mode"
                    e.presentation.description = "Switch to the terminal-based TUI interface"
                    e.presentation.icon = AllIcons.Debugger.Console
                }
            }
        }

        toolWindow.setTitleActions(listOf(agentSwitchAction, modeSwitchAction))

        installMode(AgentellIJSettings.getInstance().getMode())
    }
}
