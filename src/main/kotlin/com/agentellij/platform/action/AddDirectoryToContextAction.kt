package com.agentellij.platform.action

import com.agentellij.platform.ide.VirtualFilePaths
import com.agentellij.platform.ide.ContextSelection
import com.agentellij.platform.ide.ContextSender
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.util.concurrency.AppExecutorUtil

class AddDirectoryToContextAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = ContextSelection.hasSelection(e.dataContext)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dataContext = e.dataContext
        ReadAction.nonBlocking<List<String>> {
            ContextSelection.selectedFiles(dataContext)
                .mapNotNull { vf -> VirtualFilePaths.absolutePath(vf) }
                .distinct()
        }
            .expireWhen { project.isDisposed }
            .finishOnUiThread(ModalityState.any()) { paths ->
                if (paths.isNotEmpty()) ContextSender.insertPaths(project, paths)
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }
}
