package com.agentellij.platform.toolwindow

import com.agentellij.core.agent.AgentCatalog
import com.agentellij.core.settings.AgentModePolicy
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent

/**
 * The three controls in the tool window title bar.
 *
 * Choosing an agent and applying it are deliberately separate: switching restarts the
 * agent, so it should take a decision rather than a stray click in a dropdown.
 */
internal object AgentToolbarActions {

    fun create(installMode: (String) -> Unit): List<AnAction> {
        val pendingAgentId = AtomicReference(AgentellIJWiring.activeProfile().id)

        return listOf(
            agentSelector(pendingAgentId),
            applySelection(pendingAgentId, installMode),
            modeToggle(installMode)
        )
    }

    private fun agentSelector(pendingAgentId: AtomicReference<String>): AnAction =
        object : ComboBoxAction(), DumbAware {
            override fun getActionUpdateThread() = ActionUpdateThread.BGT

            override fun update(e: AnActionEvent) {
                val pending = AgentCatalog.allProfiles().firstOrNull { it.id == pendingAgentId.get() }
                    ?: AgentellIJWiring.activeProfile()
                e.presentation.text = pending.displayName
                e.presentation.description = "Select an agent, then click Change to apply"
            }

            override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
                val group = DefaultActionGroup()
                AgentCatalog.allProfiles().forEach { profile ->
                    group.add(object : DumbAwareAction(profile.displayName) {
                        override fun getActionUpdateThread() = ActionUpdateThread.BGT
                        override fun actionPerformed(e: AnActionEvent) = pendingAgentId.set(profile.id)
                    })
                }
                return group
            }
        }

    private fun applySelection(pendingAgentId: AtomicReference<String>, installMode: (String) -> Unit): AnAction =
        object : DumbAwareAction() {
            override fun getActionUpdateThread() = ActionUpdateThread.BGT

            override fun actionPerformed(e: AnActionEvent) {
                val target = AgentCatalog.allProfiles().firstOrNull { it.id == pendingAgentId.get() } ?: return
                if (target.id == AgentellIJWiring.activeProfile().id) return

                installMode(AgentellIJWiring.switchTo(target))
            }

            override fun update(e: AnActionEvent) {
                e.presentation.text = "Change"
                e.presentation.description = "Apply the selected agent"
                e.presentation.icon = AllIcons.Actions.Refresh
                e.presentation.isEnabled = pendingAgentId.get() != AgentellIJWiring.activeProfile().id
            }
        }

    private fun modeToggle(installMode: (String) -> Unit): AnAction =
        object : DumbAwareAction() {
            override fun getActionUpdateThread() = ActionUpdateThread.BGT

            override fun actionPerformed(e: AnActionEvent) {
                val profile = AgentellIJWiring.activeProfile()
                val requested = if (AgentellIJWiring.currentMode() == TERMINAL_MODE) GRAPHICAL_MODE else TERMINAL_MODE
                installMode(AgentellIJWiring.applyMode(requested, profile))
            }

            override fun update(e: AnActionEvent) {
                val profile = AgentellIJWiring.activeProfile()
                val canSwitch = AgentModePolicy.offersModeChoice(profile)
                e.presentation.isEnabled = canSwitch

                if (AgentellIJWiring.currentMode() == TERMINAL_MODE) {
                    e.presentation.text = if (canSwitch) "Switch to GUI Mode" else "GUI Mode Not Available"
                    e.presentation.description = if (canSwitch) {
                        "Switch to the browser-based chat interface"
                    } else {
                        "${profile.displayName} only supports TUI mode"
                    }
                    e.presentation.icon = AllIcons.General.LayoutPreviewOnly
                } else {
                    e.presentation.text = "Switch to TUI Mode"
                    e.presentation.description = "Switch to the terminal-based TUI interface"
                    e.presentation.icon = AllIcons.Debugger.Console
                }
            }
        }

    private const val TERMINAL_MODE = "tui"
    private const val GRAPHICAL_MODE = "gui"
}
