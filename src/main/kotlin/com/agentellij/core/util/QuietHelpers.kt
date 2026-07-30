package com.agentellij.core.util

/**
 * Absorbing a failure and hiding it are different things.
 *
 * Both helpers still catch everything, because narrowing what they catch would let
 * exceptions escape that the plugin has always swallowed. What they no longer do is stay
 * silent: the caller supplies somewhere to report, and a description of what was being
 * attempted, so an absorbed failure can still be found in the log.
 */

/** Closes without throwing, reporting anything that goes wrong. */
fun AutoCloseable?.closeQuietly(diagnostics: Diagnostics, what: String) {
    try {
        this?.close()
    } catch (t: Throwable) {
        diagnostics.warn("Failed to close $what", t)
    }
}

/** Runs [block], returning null and reporting when it fails. */
inline fun <T> runQuietly(diagnostics: Diagnostics, what: String, block: () -> T): T? =
    try {
        block()
    } catch (t: Throwable) {
        diagnostics.warn("Failed to $what", t)
        null
    }
