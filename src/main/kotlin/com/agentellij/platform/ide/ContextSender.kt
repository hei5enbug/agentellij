package com.agentellij.platform.ide

import com.agentellij.platform.bridge.IdeBridge
import com.agentellij.platform.toolwindow.AgentellIJWiring
import com.agentellij.platform.surface.TuiModeContent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

object ContextSender {
    private val logger = Logger.getInstance(ContextSender::class.java)

    fun insertPaths(project: Project, paths: List<String>) {
        try {
            if (paths.isEmpty()) return
            when (AgentellIJWiring.currentMode()) {
                "tui" -> writePathsToTty(project, paths)
                else -> IdeBridge.send(project, "insertPaths", mapOf("paths" to paths))
            }
        } catch (e: Exception) {
            logger.error("Unexpected error inserting paths", e)
        }
    }

    private fun writePathsToTty(project: Project, paths: List<String>) {
        val widget = TuiModeContent.getWidget(project)
        if (widget == null) {
            logger.warn("TUI terminal widget not available for project: ${project.name}")
            return
        }

        val ttyConnector = widget.ttyConnector
        if (ttyConnector == null) {
            logger.warn("TUI tty connector not available for project: ${project.name}")
            return
        }

        paths.filter { it.isNotBlank() }.forEach { path ->
            try {
                ttyConnector.write("@$path ")
            } catch (e: Exception) {
                logger.warn("Failed to write path to TUI terminal: $path", e)
            }
        }
    }
}
