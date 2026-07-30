package com.agentellij.core.util

/**
 * Where pure code reports a problem it decided to absorb.
 *
 * Swallowing a failure is sometimes the right behaviour, but swallowing it silently
 * never is. Pure code cannot reach the IDE log, so it reports through this instead and
 * the platform layer supplies an implementation that writes to the real logger.
 */
interface Diagnostics {
    fun warn(message: String, cause: Throwable? = null)

    companion object {
        /** Discards reports. Only for tests that are not asserting on them. */
        val NONE: Diagnostics = object : Diagnostics {
            override fun warn(message: String, cause: Throwable?) = Unit
        }
    }
}
