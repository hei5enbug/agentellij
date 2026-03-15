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
        var agentPath: String = "",
        var customArgs: String = ""
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(): AgentellIJSettings =
            ApplicationManager.getApplication().getService(AgentellIJSettings::class.java)
    }
}
