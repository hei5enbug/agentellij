package com.agentellij.context

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

object ContextSelection {

    /** Full multi-selection. PSI access here REQUIRES a surrounding ReadAction (see callers). */
    fun selectedFiles(dataContext: DataContext): List<VirtualFile> {
        val result = LinkedHashSet<VirtualFile>()
        dataContext.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.let { result.addAll(it) }
        dataContext.getData(PlatformCoreDataKeys.SELECTED_ITEMS)?.forEach { item ->
            toVirtualFile(item)?.let(result::add)
        }
        dataContext.getData(CommonDataKeys.VIRTUAL_FILE)?.let(result::add)
        return result.toList()
    }

    /** CHEAP, NO PSI access — safe on BGT update(). */
    fun hasSelection(dataContext: DataContext): Boolean {
        dataContext.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.let { if (it.isNotEmpty()) return true }
        dataContext.getData(PlatformCoreDataKeys.SELECTED_ITEMS)?.let { if (it.isNotEmpty()) return true }
        return dataContext.getData(CommonDataKeys.VIRTUAL_FILE) != null
    }

    private fun toVirtualFile(item: Any?): VirtualFile? = when (item) {
        is VirtualFile -> item
        is PsiFile -> item.virtualFile
        is PsiDirectory -> item.virtualFile
        is PsiElement -> item.containingFile?.virtualFile
        else -> null
    }
}
