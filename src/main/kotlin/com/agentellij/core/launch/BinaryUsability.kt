package com.agentellij.core.launch

/**
 * Decides whether a binary is worth trying to launch, before the launch is attempted.
 *
 * Catching this early lets the tool window show the install prompt instead of a process
 * failure the user cannot act on.
 *
 * A bare name that the path lookup could not resolve is treated as unusable: it would
 * only fail later, and the install prompt is the more useful answer.
 */
internal object BinaryUsability {

    fun isUsable(
        binary: String,
        resolvedBinary: String,
        isAbsolute: (String) -> Boolean,
        exists: (String) -> Boolean,
        canExecute: (String) -> Boolean
    ): Boolean {
        if (isAbsolute(resolvedBinary)) return exists(resolvedBinary) && canExecute(resolvedBinary)

        return isAbsolute(binary) && exists(binary) && canExecute(binary)
    }
}
