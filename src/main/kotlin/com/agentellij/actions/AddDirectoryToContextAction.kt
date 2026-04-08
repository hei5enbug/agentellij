package com.agentellij.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.util.concurrency.AppExecutorUtil
import com.agentellij.context.ContextSender
import com.agentellij.util.resolveAbsolutePath

class AddDirectoryToContextAction : AnAction("AgentellIJ: Add to Context") {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        e.presentation.isEnabledAndVisible = files != null && files.isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        val project = e.project ?: return

        ReadAction.nonBlocking<List<String>> {
            files.mapNotNull { vf -> vf.resolveAbsolutePath() }
        }
            .expireWith(project)
            .finishOnUiThread(ModalityState.any()) { paths ->
                if (paths.isNotEmpty()) ContextSender.insertPaths(project, paths)
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }
}
