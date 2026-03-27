package com.agentellij.actions

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext

class AgentellIJActionPromoter : ActionPromoter {

    private val priority: Map<Class<out AnAction>, Int> = mapOf(
        AddLinesToContextAction::class.java to 0,
        AddFileToContextAction::class.java to 1,
        AddDirectoryToContextAction::class.java to 2,
    )

    override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
        val ours = actions.filter { it::class.java in priority }
        if (ours.isEmpty()) return emptyList()
        val sorted = ours.sortedBy { priority[it::class.java] ?: Int.MAX_VALUE }
        return sorted + (actions - ours.toSet())
    }
}
