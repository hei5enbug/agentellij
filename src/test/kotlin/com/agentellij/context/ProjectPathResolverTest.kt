package com.agentellij.context

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ProjectPathResolverTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `relative path returns project relative path for files under project`() {
        val project = File(tempDir, "project").apply { mkdirs() }
        val file = File(project, "src/Main.kt").apply {
            parentFile.mkdirs()
            writeText("fun main() {}")
        }

        val relative = ProjectPathResolver.relativePath(file.path, project.path)

        assertEquals("src${File.separator}Main.kt", relative)
    }

    @Test
    fun `relative path keeps absolute path for files outside project`() {
        val project = File(tempDir, "project").apply { mkdirs() }
        val outside = File(tempDir, "outside.txt").apply { writeText("x") }

        val relative = ProjectPathResolver.relativePath(outside.path, project.path)

        assertEquals(outside.absoluteFile.normalize().path, relative)
    }

    @Test
    fun `relative path without project base returns normalized absolute path`() {
        val file = File(tempDir, "file.txt").apply { writeText("x") }

        val path = ProjectPathResolver.relativePath(file.path, null)

        assertEquals(file.absoluteFile.normalize().path, path)
    }

    @Test
    fun `normalize dropped files removes duplicates and normalizes paths`() {
        val file = File(tempDir, "drop.txt").apply { writeText("x") }

        val paths = ProjectPathResolver.normalizeDroppedFiles(listOf(file, File(tempDir, "./drop.txt")))

        assertEquals(listOf(file.absoluteFile.normalize().path), paths)
    }

    @Test
    fun `parse dropped text accepts quoted existing paths and removes missing paths`() {
        val first = File(tempDir, "first file.txt").apply { writeText("x") }
        val second = File(tempDir, "second.txt").apply { writeText("x") }
        val text = "\"${first.path}\"\n${second.path}\n${File(tempDir, "missing.txt").path}"

        val paths = ProjectPathResolver.parseDroppedText(text)

        assertEquals(
            listOf(first.absoluteFile.normalize().path, second.absoluteFile.normalize().path),
            paths
        )
    }
}
