package com.agentellij.context

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.agentellij.bridge.IdeBridge

object ContextSender {
    private val logger = Logger.getInstance(ContextSender::class.java)

    fun insertPaths(project: Project, paths: List<String>) {
        try {
            if (paths.isEmpty()) return
            IdeBridge.send(project, "insertPaths", mapOf("paths" to paths))
        } catch (e: Exception) {
            logger.error("Unexpected error inserting paths", e)
        }
    }

    fun pastePath(project: Project, path: String) {
        try {
            if (path.isEmpty()) return
            IdeBridge.send(project, "pastePath", mapOf("path" to path))
        } catch (e: Exception) {
            logger.error("Unexpected error pasting path", e)
        }
    }
}
