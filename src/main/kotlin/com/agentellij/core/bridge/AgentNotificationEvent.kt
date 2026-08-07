package com.agentellij.core.bridge

/** The two main-agent lifecycle points that require the user's attention. */
internal enum class AgentNotificationEvent(
    val route: String,
    val titleSuffix: String,
    val message: String
) {
    TURN_COMPLETED(
        route = BridgeRoutes.AGENT_TURN_COMPLETED,
        titleSuffix = "response completed",
        message = "The agent is ready for your next message."
    ),
    INPUT_REQUESTED(
        route = BridgeRoutes.AGENT_INPUT_REQUESTED,
        titleSuffix = "needs your input",
        message = "The agent is waiting for your answer."
    );

    companion object {
        fun fromRoute(route: String?): AgentNotificationEvent? = entries.firstOrNull { it.route == route }
    }
}
