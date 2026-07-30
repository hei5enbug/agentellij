package com.agentellij.core.state

import com.agentellij.core.util.Diagnostics
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * Reads and writes a single JSON object file belonging to the agent.
 *
 * The file is shared with a running agent process, so a write must never leave a
 * half-written file behind: content goes to a temporary file first and is then moved
 * into place in one step. Some filesystems cannot do that atomically, in which case a
 * plain move is used instead.
 *
 * A file that cannot be parsed is backed up rather than overwritten, because it may be
 * the user's only copy of the agent's conversation state.
 *
 * The file to act on is always supplied by the caller: this class never goes looking
 * for one, which is what keeps it testable against a temporary directory.
 */
internal class JsonObjectStore(
    private val mapper: ObjectMapper,
    private val diagnostics: Diagnostics = Diagnostics.NONE,
    private val moveAtomically: (Path, Path) -> Unit = { source, target ->
        Files.move(source, target, REPLACE_EXISTING, ATOMIC_MOVE)
    },
    private val movePlainly: (Path, Path) -> Unit = { source, target ->
        Files.move(source, target, REPLACE_EXISTING)
    }
) {

    fun read(file: File): ObjectNode {
        return try {
            if (!file.exists()) return mapper.createObjectNode()

            val tree = mapper.readTree(file.readText())
            if (tree is ObjectNode) {
                tree
            } else {
                backup(file, "Expected JSON object but found ${tree.nodeType}")
                mapper.createObjectNode()
            }
        } catch (e: Exception) {
            backup(file, "Failed to parse JSON state file", e)
            mapper.createObjectNode()
        }
    }

    fun write(file: File, data: ObjectNode) {
        file.parentFile.mkdirs()
        val temp = File.createTempFile(file.name, ".tmp", file.parentFile)
        try {
            temp.writeText(mapper.writeValueAsString(data))
            try {
                moveAtomically(temp.toPath(), file.toPath())
            } catch (_: AtomicMoveNotSupportedException) {
                movePlainly(temp.toPath(), file.toPath())
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun backup(file: File, message: String, cause: Exception? = null) {
        if (!file.exists()) return
        if (hasBackupWithSameContent(file)) return

        val backup = nextBackupFile(file)
        try {
            Files.copy(file.toPath(), backup.toPath())
            diagnostics.warn("$message: ${file.absolutePath}. Backup created at ${backup.absolutePath}", cause)
        } catch (backupError: Exception) {
            diagnostics.warn("$message: ${file.absolutePath}. Failed to create backup", backupError)
        }
    }

    private fun hasBackupWithSameContent(file: File): Boolean {
        var candidate = File(file.parentFile, "${file.name}$BACKUP_SUFFIX")
        var index = 0
        while (candidate.exists()) {
            if (sameContent(file, candidate)) return true
            index += 1
            candidate = File(file.parentFile, "${file.name}$BACKUP_SUFFIX.$index")
        }
        return false
    }

    private fun sameContent(left: File, right: File): Boolean = try {
        left.readBytes().contentEquals(right.readBytes())
    } catch (_: Exception) {
        false
    }

    private fun nextBackupFile(file: File): File {
        val base = File(file.parentFile, "${file.name}$BACKUP_SUFFIX")
        if (!base.exists()) return base

        var index = 1
        while (true) {
            val candidate = File(file.parentFile, "${file.name}$BACKUP_SUFFIX.$index")
            if (!candidate.exists()) return candidate
            index += 1
        }
    }

    private companion object {
        const val BACKUP_SUFFIX = ".corrupt"
    }
}
