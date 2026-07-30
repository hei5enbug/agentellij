package com.agentellij.platform.action

import com.agentellij.core.context.ContextActionKind
import com.agentellij.core.context.ContextActionOrder
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext

class AgentellIJActionPromoter : ActionPromoter {

    override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> =
        ContextActionOrder.promote(
            candidates = actions,
            editorFocused = context.getData(CommonDataKeys.EDITOR) != null,
            kindOf = ::kindOf
        )

    private fun kindOf(action: AnAction): ContextActionKind? = when (action) {
        is AddLinesToContextAction -> ContextActionKind.LINES
        is AddFileToContextAction -> ContextActionKind.FILE
        is AddDirectoryToContextAction -> ContextActionKind.DIRECTORY
        else -> null
    }
}
