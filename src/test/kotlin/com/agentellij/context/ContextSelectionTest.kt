package com.agentellij.context

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContextSelectionTest {

    @Test
    fun `selectedFiles returns virtual file array contents`() {
        val vf1 = LightVirtualFile("a.kt")
        val vf2 = LightVirtualFile("b.kt")

        val files = ContextSelection.selectedFiles(
            ctx(CommonDataKeys.VIRTUAL_FILE_ARRAY.name to arrayOf<VirtualFile>(vf1, vf2))
        )

        assertIdentityList(listOf(vf1, vf2), files)
    }

    @Test
    fun `selectedFiles returns selected items when array is null`() {
        val vf1 = LightVirtualFile("a.kt")
        val vf2 = LightVirtualFile("b.kt")

        val files = ContextSelection.selectedFiles(
            ctx(
                CommonDataKeys.VIRTUAL_FILE_ARRAY.name to null,
                PlatformCoreDataKeys.SELECTED_ITEMS.name to arrayOf<Any>(vf1, vf2)
            )
        )

        assertIdentityList(listOf(vf1, vf2), files)
    }

    @Test
    fun `selectedFiles deduplicates overlapping array and selected items`() {
        val vf1 = LightVirtualFile("a.kt")
        val vf2 = LightVirtualFile("b.kt")

        val files = ContextSelection.selectedFiles(
            ctx(
                CommonDataKeys.VIRTUAL_FILE_ARRAY.name to arrayOf<VirtualFile>(vf1),
                PlatformCoreDataKeys.SELECTED_ITEMS.name to arrayOf<Any>(vf1, vf2)
            )
        )

        assertIdentityList(listOf(vf1, vf2), files)
    }

    @Test
    fun `selectedFiles returns single virtual file fallback`() {
        val vf1 = LightVirtualFile("a.kt")

        val files = ContextSelection.selectedFiles(
            ctx(CommonDataKeys.VIRTUAL_FILE.name to vf1)
        )

        assertIdentityList(listOf(vf1), files)
    }

    @Test
    fun `selectedFiles returns empty list for empty context`() {
        val files = ContextSelection.selectedFiles(ctx())

        assertTrue(files.isEmpty())
    }

    @Test
    fun `hasSelection is true when virtual file array is non-empty`() {
        val vf1 = LightVirtualFile("a.kt")

        assertTrue(
            ContextSelection.hasSelection(
                ctx(CommonDataKeys.VIRTUAL_FILE_ARRAY.name to arrayOf<VirtualFile>(vf1))
            )
        )
    }

    @Test
    fun `hasSelection is true when selected items is non-empty`() {
        val vf1 = LightVirtualFile("a.kt")

        assertTrue(
            ContextSelection.hasSelection(
                ctx(PlatformCoreDataKeys.SELECTED_ITEMS.name to arrayOf<Any>(vf1))
            )
        )
    }

    @Test
    fun `hasSelection is true when virtual file is non-null`() {
        val vf1 = LightVirtualFile("a.kt")

        assertTrue(
            ContextSelection.hasSelection(
                ctx(CommonDataKeys.VIRTUAL_FILE.name to vf1)
            )
        )
    }

    @Test
    fun `hasSelection is false when array is empty and nothing else exists`() {
        assertFalse(
            ContextSelection.hasSelection(
                ctx(CommonDataKeys.VIRTUAL_FILE_ARRAY.name to arrayOf<VirtualFile>())
            )
        )
    }

    @Test
    fun `hasSelection is false when context is empty`() {
        assertFalse(ContextSelection.hasSelection(ctx()))
    }

    private fun ctx(vararg pairs: Pair<String, Any?>): DataContext {
        val map = pairs.toMap()
        return DataContext { dataId -> map[dataId] }
    }

    private fun assertIdentityList(expected: List<VirtualFile>, actual: List<VirtualFile>) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { index -> assertSame(expected[index], actual[index]) }
    }
}
