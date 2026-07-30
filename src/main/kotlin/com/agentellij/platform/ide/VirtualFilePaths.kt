package com.agentellij.platform.ide

import com.intellij.openapi.diagnostic.Logger
import com.agentellij.platform.env.IdeLoggerDiagnostics
import com.agentellij.core.context.ProjectPaths
import com.agentellij.core.util.runQuietly
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

/**
 * Turns the IDE's file objects into the plain paths the agent understands.
 *
 * A file open in the editor is not always a real file on disk, so the local filesystem
 * conversion is attempted first and the IDE's own path is used otherwise.
 */
object VirtualFilePaths {

    private val diagnostics = IdeLoggerDiagnostics(Logger.getInstance(VirtualFilePaths::class.java))

    fun absolutePath(file: VirtualFile): String? = runQuietly(diagnostics, "read the path of ${file.name}") {
        if (file.isInLocalFileSystem) VfsUtilCore.virtualToIoFile(file).absolutePath else file.path
    }

    fun relativePath(file: VirtualFile?, projectBasePath: String?): String? {
        if (file == null) return null
        val absolutePath = absolutePath(file) ?: return file.path

        return ProjectPaths.relativePath(absolutePath, projectBasePath, file.name)
    }
}
