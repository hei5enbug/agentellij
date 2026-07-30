package com.agentellij.core.settings

/** A new edit state plus the path the settings panel should now display. */
internal data class AgentSelectionResult(val state: AgentPathEditState, val pathToShow: String)

/** A new edit state plus every path that should be written to the settings service. */
internal data class AgentPathsSnapshot(val state: AgentPathEditState, val paths: Map<String, String>)

/**
 * Remembers the path the user typed for each agent while the settings panel is open.
 *
 * The panel has one path field but several agents, so switching agents has to park the
 * current text somewhere and bring back the text belonging to the agent being switched
 * to. Nothing is written to the settings service until the user applies the panel.
 *
 * Every operation returns a new state rather than editing this one.
 */
internal class AgentPathEditState private constructor(
    private val pathsByAgentId: Map<String, String>,
    private val currentAgentId: String
) {

    fun currentPath(): String = pathsByAgentId[currentAgentId].orEmpty()

    /** Parks the text currently in the field, then switches to [agentId]. */
    fun selectAgent(agentId: String, currentPath: String): AgentSelectionResult {
        val parked = pathsByAgentId + (currentAgentId to currentPath)
        return AgentSelectionResult(
            state = AgentPathEditState(parked, agentId),
            pathToShow = parked[agentId].orEmpty()
        )
    }

    /** Parks the text currently in the field, then reports every remembered path. */
    fun snapshot(selectedAgentId: String, currentPath: String): AgentPathsSnapshot {
        val parked = pathsByAgentId + (selectedAgentId to currentPath)
        return AgentPathsSnapshot(
            state = AgentPathEditState(parked, selectedAgentId),
            paths = parked
        )
    }

    companion object {
        fun initial(
            agentIds: List<String>,
            selectedAgentId: String,
            pathProvider: (String) -> String
        ): AgentPathEditState = AgentPathEditState(
            pathsByAgentId = agentIds.associateWith(pathProvider),
            currentAgentId = selectedAgentId
        )
    }
}
