package com.agentellij.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.agentellij.context.ContextSender
import com.agentellij.util.resolveAbsolutePath

class AddFileToContextAction : AnAction("AgentellIJ: Add File to Context") {

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val project = e.project ?: return
        val path = file.resolveAbsolutePath() ?: return

        if (path.isNotEmpty()) {
            ContextSender.insertPaths(project, listOf(path))
        }
    }
}
