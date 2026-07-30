package com.agentellij.platform.ide

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

/**
 * A platform class, kept under test because it runs without a heavy IDE fixture and the
 * multi-selection ordering is behaviour the user feels directly.
 */
class ContextSelectionSpec : BehaviorSpec({

    fun context(vararg pairs: Pair<String, Any?>): DataContext {
        val map = pairs.toMap()
        return DataContext { dataId -> map[dataId] }
    }

    Given("a selection handed over by the IDE") {

        When("the selection arrives as a virtual file array") {
            val first = LightVirtualFile("a.kt")
            val second = LightVirtualFile("b.kt")

            Then("every file in the array is collected in order") {
                ContextSelection.selectedFiles(
                    context(CommonDataKeys.VIRTUAL_FILE_ARRAY.name to arrayOf<VirtualFile>(first, second))
                ) shouldBe listOf(first, second)
            }
        }

        When("the array is absent but selected items are present") {
            val first = LightVirtualFile("a.kt")
            val second = LightVirtualFile("b.kt")

            Then("the selected items are used instead") {
                ContextSelection.selectedFiles(
                    context(
                        CommonDataKeys.VIRTUAL_FILE_ARRAY.name to null,
                        PlatformCoreDataKeys.SELECTED_ITEMS.name to arrayOf<Any>(first, second)
                    )
                ) shouldBe listOf(first, second)
            }
        }

        When("the array and the selected items overlap") {
            val first = LightVirtualFile("a.kt")
            val second = LightVirtualFile("b.kt")

            Then("the overlapping file appears only once") {
                ContextSelection.selectedFiles(
                    context(
                        CommonDataKeys.VIRTUAL_FILE_ARRAY.name to arrayOf<VirtualFile>(first),
                        PlatformCoreDataKeys.SELECTED_ITEMS.name to arrayOf<Any>(first, second)
                    )
                ) shouldBe listOf(first, second)
            }
        }

        When("only a single virtual file is available") {
            val only = LightVirtualFile("a.kt")

            Then("that file is used as the fallback") {
                ContextSelection.selectedFiles(
                    context(CommonDataKeys.VIRTUAL_FILE.name to only)
                ) shouldBe listOf(only)
            }
        }

        When("nothing is selected") {
            Then("no files are collected") {
                ContextSelection.selectedFiles(context()).shouldBeEmpty()
            }
        }
    }

    Given("an action deciding whether to enable itself") {

        When("something is selected") {
            Then("a non-empty file array counts as a selection") {
                ContextSelection.hasSelection(
                    context(CommonDataKeys.VIRTUAL_FILE_ARRAY.name to arrayOf<VirtualFile>(LightVirtualFile("a.kt")))
                ) shouldBe true
            }

            Then("non-empty selected items count as a selection") {
                ContextSelection.hasSelection(
                    context(PlatformCoreDataKeys.SELECTED_ITEMS.name to arrayOf<Any>(LightVirtualFile("a.kt")))
                ) shouldBe true
            }

            Then("a single virtual file counts as a selection") {
                ContextSelection.hasSelection(
                    context(CommonDataKeys.VIRTUAL_FILE.name to LightVirtualFile("a.kt"))
                ) shouldBe true
            }
        }

        When("nothing is selected") {
            Then("an empty file array is not a selection") {
                ContextSelection.hasSelection(
                    context(CommonDataKeys.VIRTUAL_FILE_ARRAY.name to arrayOf<VirtualFile>())
                ) shouldBe false
            }

            Then("an empty context is not a selection") {
                ContextSelection.hasSelection(context()) shouldBe false
            }
        }
    }
})
