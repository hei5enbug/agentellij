package com.agentellij.ui

import com.agentellij.backend.AgentProfileResolver
import com.agentellij.settings.AgentModePolicy
import com.agentellij.settings.AgentellIJSettings
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent

class ChatToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val modeDisposableRef = AtomicReference<Disposable?>(null)

        fun installMode(mode: String) {
            val settings = AgentellIJSettings.getInstance()
            val profile = AgentProfileResolver.resolve()
            val normalizedMode = AgentModePolicy.normalizeModeForProfile(mode, profile)
            if (settings.state.mode != normalizedMode) {
                settings.state.mode = normalizedMode
            }

            modeDisposableRef.getAndSet(null)?.let { Disposer.dispose(it) }

            toolWindow.contentManager.contents.toList().forEach {
                toolWindow.contentManager.removeContent(it, true)
            }

            val modeDisposable = Disposer.newDisposable("agentellij-mode-$normalizedMode")
            Disposer.register(toolWindow.disposable, modeDisposable)
            modeDisposableRef.set(modeDisposable)

            when (normalizedMode) {
                "tui" -> TuiModeContent(project, toolWindow, modeDisposable).install()
                else  -> GuiModeContent(project, toolWindow, modeDisposable).install()
            }
        }

        val pendingAgentRef = AtomicReference(AgentProfileResolver.resolve().id)

        val agentSelectAction = object : ComboBoxAction(), DumbAware {
            override fun getActionUpdateThread() = ActionUpdateThread.BGT

            override fun update(e: AnActionEvent) {
                val pendingId = pendingAgentRef.get()
                val profile = AgentProfileResolver.allProfiles().firstOrNull { it.id == pendingId }
                    ?: AgentProfileResolver.resolve()
                e.presentation.text = profile.displayName
                e.presentation.description = "Select an agent, then click Change to apply"
            }

            override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
                val group = DefaultActionGroup()
                AgentProfileResolver.allProfiles().forEach { profile ->
                    group.add(object : DumbAwareAction(profile.displayName) {
                        override fun getActionUpdateThread() = ActionUpdateThread.BGT
                        override fun actionPerformed(e: AnActionEvent) {
                            pendingAgentRef.set(profile.id)
                        }
                    })
                }
                return group
            }
        }

        val changeAction = object : DumbAwareAction() {
            override fun getActionUpdateThread() = ActionUpdateThread.BGT

            override fun actionPerformed(e: AnActionEvent) {
                val settings = AgentellIJSettings.getInstance()
                val target = AgentProfileResolver.allProfiles().firstOrNull { it.id == pendingAgentRef.get() } ?: return
                if (target.id == AgentProfileResolver.resolve().id) return

                settings.state.activeAgent = target.id
                settings.state.mode = AgentModePolicy.normalizeModeForProfile(settings.getMode(), target)

                installMode(settings.getMode())
            }

            override fun update(e: AnActionEvent) {
                e.presentation.text = "Change"
                e.presentation.description = "Apply the selected agent"
                e.presentation.icon = AllIcons.Actions.Refresh
                e.presentation.isEnabled = pendingAgentRef.get() != AgentProfileResolver.resolve().id
            }
        }

        val modeSwitchAction = object : DumbAwareAction() {
            override fun getActionUpdateThread() = ActionUpdateThread.BGT

            override fun actionPerformed(e: AnActionEvent) {
                val settings = AgentellIJSettings.getInstance()
                val profile = AgentProfileResolver.resolve()
                val newMode = if (settings.getMode() == "tui") "gui" else "tui"
                val normalizedMode = AgentModePolicy.normalizeModeForProfile(newMode, profile)
                settings.state.mode = normalizedMode
                installMode(normalizedMode)
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

        toolWindow.setTitleActions(listOf(agentSelectAction, changeAction, modeSwitchAction))

        installMode(AgentellIJSettings.getInstance().getMode())
    }
}
