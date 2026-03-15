package com.agentellij.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.agentellij.context.ContextSender
import com.agentellij.util.resolveAbsolutePath

class AddFromProjectTreeAction : AnAction("AgentellIJ: Add to Context") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        val paths = files.mapNotNull { it.resolveAbsolutePath() }

        if (paths.isNotEmpty()) {
            ContextSender.insertPaths(project, paths)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) != null
    }
}
