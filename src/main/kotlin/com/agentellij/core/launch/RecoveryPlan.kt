package com.agentellij.core.launch

/** What to do after the operating system refused to start an agent process. */
internal sealed interface ProcessRecovery {
    /** The file is there but not marked executable. Grant the bit and try again. */
    data object SetExecutableAndRetry : ProcessRecovery

    /** The named file is missing but another copy was found. Try that instead. */
    data class RetryWithAlternative(val path: String) : ProcessRecovery

    /** Nothing sensible left to try; report the original failure. */
    data object GiveUp : ProcessRecovery
}

/**
 * Chooses a recovery for a process that would not start.
 *
 * Two failures are worth recovering from because both are common and both have an
 * obvious remedy: an agent downloaded without its executable bit, and an agent that
 * moved after the path to it was remembered.
 */
internal object RecoveryPlan {

    fun decide(
        binaryPath: String?,
        exists: Boolean,
        canExecute: Boolean,
        alternative: String?
    ): ProcessRecovery = when {
        binaryPath == null -> ProcessRecovery.GiveUp
        exists && !canExecute -> ProcessRecovery.SetExecutableAndRetry
        !exists && alternative != null -> ProcessRecovery.RetryWithAlternative(alternative)
        else -> ProcessRecovery.GiveUp
    }
}
