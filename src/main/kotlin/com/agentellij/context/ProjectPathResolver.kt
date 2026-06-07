package com.agentellij.context

import com.agentellij.util.runQuietly
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.nio.file.Paths

object ProjectPathResolver {
    fun absolutePath(vf: VirtualFile): String? = runQuietly {
        if (vf.isInLocalFileSystem) VfsUtilCore.virtualToIoFile(vf).absolutePath else vf.path
    }

    fun relativePath(vf: VirtualFile?, projectBasePath: String?): String? {
        if (vf == null) return null
        val absolutePath = absolutePath(vf) ?: return vf.path
        return relativePath(absolutePath, projectBasePath, vf.name)
    }

    fun relativePath(path: String, projectBasePath: String?, fallbackName: String? = null): String {
        val normalizedPath = normalizePath(path)
        if (projectBasePath.isNullOrBlank()) return normalizedPath.ifEmpty { fallbackName ?: path }

        return runQuietly {
            val filePath = Paths.get(normalizedPath).toAbsolutePath().normalize()
            val base = Paths.get(projectBasePath).toAbsolutePath().normalize()
            val relative = if (filePath.startsWith(base)) base.relativize(filePath) else filePath
            relative.toString().ifEmpty { fallbackName ?: normalizedPath }
        } ?: fallbackRelativePath(normalizedPath, projectBasePath, fallbackName)
    }

    fun normalizeDroppedFiles(files: List<File>): List<String> =
        files.asSequence()
            .map { it.absolutePath }
            .map(::normalizePath)
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

    fun parseDroppedText(text: String): List<String> =
        text.lineSequence()
            .map { it.trim().trimMatchingQuotes() }
            .filter { it.isNotBlank() }
            .filter { File(it).exists() }
            .map(::normalizePath)
            .distinct()
            .toList()

    private fun normalizePath(path: String): String =
        runQuietly { File(path).absoluteFile.normalize().path } ?: path

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
