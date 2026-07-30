package com.agentellij.core.context

import com.agentellij.core.util.Diagnostics
import com.agentellij.core.util.runQuietly
import java.io.File
import java.nio.file.Paths

/**
 * Shapes the file paths handed to the agent.
 *
 * Paths inside the project are shortened so the agent sees what the user sees in the
 * project tree; anything outside stays absolute, because a relative path would be
 * meaningless from the project root.
 */
object ProjectPaths {

    /**
     * @param fallbackName used when relativising leaves nothing, which happens when the
     *   path is the project root itself.
     */
    fun relativePath(
        path: String,
        projectBasePath: String?,
        fallbackName: String? = null,
        diagnostics: Diagnostics = Diagnostics.NONE
    ): String {
        val normalizedPath = normalizePath(path, diagnostics)
        if (projectBasePath.isNullOrBlank()) return normalizedPath.ifEmpty { fallbackName ?: path }

        return runQuietly(diagnostics, "relativise $normalizedPath") {
            val filePath = Paths.get(normalizedPath).toAbsolutePath().normalize()
            val base = Paths.get(projectBasePath).toAbsolutePath().normalize()
            val relative = if (filePath.startsWith(base)) base.relativize(filePath) else filePath
            relative.toString().ifEmpty { fallbackName ?: normalizedPath }
        } ?: fallbackRelativePath(normalizedPath, projectBasePath, fallbackName)
    }

    fun normalizeDroppedFiles(files: List<File>, diagnostics: Diagnostics = Diagnostics.NONE): List<String> =
        files.asSequence()
            .map { it.absolutePath }
            .map { normalizePath(it, diagnostics) }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

    /**
     * Reads paths out of dropped text.
     *
     * A drop can carry anything, so a line only counts as a path when it names something
     * that is actually there. Existence is asked of the caller rather than checked here,
     * which keeps the parsing rules testable.
     */
    fun parseDroppedText(
        text: String,
        diagnostics: Diagnostics = Diagnostics.NONE,
        exists: (String) -> Boolean
    ): List<String> =
        text.lineSequence()
            .map { it.trim().trimMatchingQuotes() }
            .filter { it.isNotBlank() }
            .filter(exists)
            .map { normalizePath(it, diagnostics) }
            .distinct()
            .toList()

    private fun normalizePath(path: String, diagnostics: Diagnostics): String =
        runQuietly(diagnostics, "normalise $path") { File(path).absoluteFile.normalize().path } ?: path

    private fun fallbackRelativePath(path: String, projectBasePath: String, fallbackName: String?): String {
        val base = File(projectBasePath).absoluteFile.normalize().path
        val relative = if (path.startsWith(base + File.separator)) path.substring(base.length + 1) else path
        return relative.ifEmpty { fallbackName ?: path }
    }

    private fun String.trimMatchingQuotes(): String {
        if (length < 2) return this
        val first = first()
        val last = last()
        return if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            substring(1, length - 1)
        } else {
            this
        }
    }
}
