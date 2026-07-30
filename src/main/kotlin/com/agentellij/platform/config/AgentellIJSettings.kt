package com.agentellij.platform.config

import com.agentellij.core.settings.AgentModePolicy
import com.agentellij.core.settings.AgentPaths
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
        var codexAgentPath: String = "",
        var customArgs: String = ""
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state.copy(mode = normalizeMode(state.mode))
    }

    fun getMode(): String = normalizeMode(state.mode)

    fun getActiveAgent(): String = state.activeAgent.ifBlank { "opencode" }

    fun getAgentPath(agentId: String): String = agentPaths().pathFor(agentId)

    fun setAgentPath(agentId: String, path: String) {
        val updated = agentPaths().withPath(agentId, path)
        state.agentPath = updated.shared
        state.claudeAgentPath = updated.claude
        state.codexAgentPath = updated.codex
    }

    private fun agentPaths(): AgentPaths = AgentPaths(
        shared = state.agentPath,
        claude = state.claudeAgentPath,
        codex = state.codexAgentPath
    )

    companion object {
        fun normalizeMode(mode: String?): String =
            AgentModePolicy.normalizeMode(mode)

        fun getInstance(): AgentellIJSettings =
            ApplicationManager.getApplication().getService(AgentellIJSettings::class.java)
    }
}
