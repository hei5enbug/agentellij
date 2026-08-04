package com.agentellij.core.bridge

/** Decisions shared by web and terminal completion signals before the IDE is touched. */
internal object AgentCompletionPolicy {
    const val DUPLICATE_WINDOW_MILLIS = 1_500L

    fun supportedAgentId(rawAgentId: String?, supportedAgentIds: Set<String>): String? =
        rawAgentId?.trim()?.takeIf { it in supportedAgentIds }

    /**
     * Two adapters can observe the same transition (for example, both `session.status`
     * and `session.idle`). A short per-session window keeps that from producing two
     * balloons while still allowing the next genuine turn through.
     */
    fun shouldDeliver(previousMillis: Long?, nowMillis: Long): Boolean =
        previousMillis == null || nowMillis < previousMillis ||
            nowMillis - previousMillis >= DUPLICATE_WINDOW_MILLIS
}
