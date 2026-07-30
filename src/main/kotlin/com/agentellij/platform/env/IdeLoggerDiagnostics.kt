package com.agentellij.platform.env

import com.agentellij.core.util.Diagnostics
import com.intellij.openapi.diagnostic.Logger

/**
 * Routes reports from pure code into the IDE log.
 *
 * This is the platform half of the Diagnostics contract: pure code decides what is
 * worth reporting, this decides where it goes.
 */
class IdeLoggerDiagnostics(private val logger: Logger) : Diagnostics {
    override fun warn(message: String, cause: Throwable?) {
        if (cause == null) logger.warn(message) else logger.warn(message, cause)
    }
}
