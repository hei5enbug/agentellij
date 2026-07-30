package com.agentellij.platform.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.lang.reflect.Proxy

class AgentellIJActionPromoterSpec : BehaviorSpec({

    val promoter = AgentellIJActionPromoter()
    val fileAction = AddFileToContextAction()
    val directoryAction = AddDirectoryToContextAction()
    val linesAction = AddLinesToContextAction()

    fun context(editor: Editor?, fileArray: Array<VirtualFile>? = null): DataContext =
        DataContext { dataId ->
            when (dataId) {
                CommonDataKeys.EDITOR.name -> editor
                CommonDataKeys.VIRTUAL_FILE_ARRAY.name -> fileArray
                else -> null
            }
        }

    fun fakeEditor(): Editor = Proxy.newProxyInstance(
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

    Given("the shared context shortcut pressed while the editor has focus") {

        When("all three context actions are candidates") {
            Then("adding the selected lines takes priority") {
                promoter.promote(
                    listOf(fileAction, directoryAction, linesAction),
                    context(editor = fakeEditor())
                ).first() shouldBe linesAction
            }
        }

        When("the lines action is not a candidate") {
            Then("adding the current file takes priority over the tree action") {
                promoter.promote(
                    listOf(directoryAction, fileAction),
                    context(editor = fakeEditor())
                ).first() shouldBe fileAction
            }
        }
    }

    Given("the shared context shortcut pressed outside the editor") {

        When("the project tree reports a single selected file") {
            Then("the tree action still takes priority") {
                promoter.promote(
                    listOf(fileAction, directoryAction),
                    context(editor = null, fileArray = arrayOf<VirtualFile>(LightVirtualFile("a.kt")))
                ).first() shouldBe directoryAction
            }
        }

        When("no file array is reported at all") {
            Then("the tree action still takes priority") {
                promoter.promote(
                    listOf(fileAction, directoryAction),
                    context(editor = null, fileArray = null)
                ).first() shouldBe directoryAction
            }
        }

        When("actions from other plugins are candidates too") {
            val unrelated = object : AnAction() {
                override fun actionPerformed(e: AnActionEvent) = Unit
            }
            val promoted = promoter.promote(
                listOf(unrelated, fileAction, directoryAction),
                context(editor = null)
            )

            Then("our action is still promoted to the front") {
                promoted.first() shouldBe directoryAction
            }

            Then("the unrelated action is preserved rather than dropped") {
                promoted shouldContain unrelated
            }
        }
    }
})
