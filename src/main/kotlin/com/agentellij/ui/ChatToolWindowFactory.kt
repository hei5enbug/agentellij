package com.agentellij.ui

import com.agentellij.settings.AgentellIJSettings
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

class ChatToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        when (AgentellIJSettings.getInstance().getMode()) {
            "tui" -> TuiModeContent(project, toolWindow).install()

            else -> GuiModeContent(project, toolWindow).install()
        }
    }
}
