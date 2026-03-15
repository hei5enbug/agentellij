package com.agentellij.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.agentellij.context.ContextSender
import com.agentellij.util.resolveAbsolutePath

class AddLinesToContextAction : AnAction("AgentellIJ: Add Lines to Context") {
    override fun update(e: AnActionEvent) {
        val editor: Editor? = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor?.selectionModel?.hasSelection() == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val project = e.project ?: return

        val sel = editor.selectionModel
        if (!sel.hasSelection()) return

        val doc = editor.document
        val startLine = doc.getLineNumber(sel.selectionStart) + 1
        val endLine = doc.getLineNumber((sel.selectionEnd - 1).coerceAtLeast(0)) + 1

        val basePath = file.resolveAbsolutePath() ?: return
        val pathWithRange = "$basePath:$startLine-$endLine"
        ContextSender.insertPaths(project, listOf(pathWithRange))
    }
}
