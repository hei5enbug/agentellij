package com.agentellij.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class AgentellIJActionPromoterTest {

    private val promoter = AgentellIJActionPromoter()
    private val fileAction = AddFileToContextAction()
    private val directoryAction = AddDirectoryToContextAction()
    private val linesAction = AddLinesToContextAction()

    @Test
    fun `editor present promotes lines action first`() {
        val promoted = promoter.promote(
            listOf(fileAction, directoryAction, linesAction),
            ctx(editor = fakeEditor())
        )
        assertEquals(linesAction, promoted.first())
    }

    @Test
    fun `editor present without lines promotes file action over directory`() {
        val promoted = promoter.promote(listOf(directoryAction, fileAction), ctx(editor = fakeEditor()))
        assertEquals(fileAction, promoted.first())
    }

    @Test
    fun `no editor promotes directory action even when file array is a single file`() {
        val promoted = promoter.promote(
            listOf(fileAction, directoryAction),
            ctx(editor = null, fileArray = arrayOf<VirtualFile>(LightVirtualFile("a.kt")))
        )
        assertEquals(directoryAction, promoted.first())
    }

    @Test
    fun `no editor promotes directory action even when file array is null`() {
        val promoted = promoter.promote(listOf(fileAction, directoryAction), ctx(editor = null, fileArray = null))
        assertEquals(directoryAction, promoted.first())
    }

    @Test
    fun `no editor preserves unrelated actions after promoted ones`() {
        val unrelated = object : AnAction() {
            override fun actionPerformed(e: AnActionEvent) = Unit
        }
        val promoted = promoter.promote(listOf(unrelated, fileAction, directoryAction), ctx(editor = null))
        assertEquals(directoryAction, promoted.first())
        assertTrue(promoted.contains(unrelated))
    }

    private fun ctx(editor: Editor?, fileArray: Array<VirtualFile>? = null): DataContext =
        DataContext { dataId ->
            when (dataId) {
                CommonDataKeys.EDITOR.name -> editor
                CommonDataKeys.VIRTUAL_FILE_ARRAY.name -> fileArray
                else -> null
            }
        }

    private fun fakeEditor(): Editor = Proxy.newProxyInstance(
        Editor::class.java.classLoader,
        arrayOf(Editor::class.java)
    ) { _, method, _ ->
        when (method.name) {
            "equals" -> false
            "hashCode" -> 0
            "toString" -> "FakeEditor"
            else -> null
        }
    } as Editor
}
