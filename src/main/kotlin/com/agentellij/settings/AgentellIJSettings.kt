package com.agentellij.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "com.agentellij.settings.AgentellIJSettings",
    storages = [Storage("AgentellIJSettings.xml")]
)
@Service
class AgentellIJSettings : PersistentStateComponent<AgentellIJSettings.State> {
    data class State(
        var mode: String = "tui",
        var activeAgent: String = "opencode",
        var agentPath: String = "",
        var claudeAgentPath: String = "",
        var customArgs: String = ""
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state.copy(mode = normalizeMode(state.mode))
    }

    fun getMode(): String = normalizeMode(state.mode)

    fun getActiveAgent(): String = state.activeAgent.ifBlank { "opencode" }

    fun getAgentPath(agentId: String): String = when (agentId) {
        "claude" -> state.claudeAgentPath
        else -> state.agentPath
    }

    fun setAgentPath(agentId: String, path: String) {
        when (agentId) {
            "claude" -> state.claudeAgentPath = path
            else -> state.agentPath = path
        }
    }

    companion object {
        fun normalizeMode(mode: String?): String =
            when (mode?.lowercase()) {
                "gui", "tui" -> mode.lowercase()
                else -> "tui"
            }

        fun getInstance(): AgentellIJSettings =
            ApplicationManager.getApplication().getService(AgentellIJSettings::class.java)
    }
}
