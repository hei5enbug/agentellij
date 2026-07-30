package com.agentellij.core.launch

object TerminalShellCommand {
    fun renderInner(command: List<String>, isWindows: Boolean): String =
        command.joinToString(" ") { quote(it, isWindows) }

    fun wrap(
        command: List<String>,
        isWindows: Boolean,
        env: (String) -> String?,
        isExecutable: (String) -> Boolean,
        fileExists: (String) -> Boolean
    ): List<String> {
        if (isWindows) {
            val comspec = env("ComSpec")?.takeIf { it.isNotBlank() } ?: "cmd.exe"
            return listOf(comspec, "/k", renderInner(command, isWindows = true))
        }

        val shell = env("SHELL")?.takeIf { it.isNotBlank() && isExecutable(it) }
            ?: listOf("/bin/zsh", "/bin/bash", "/bin/sh").firstOrNull { fileExists(it) }
            ?: "/bin/sh"
        return listOf(shell, "-l", "-i", "-c", renderInner(command, isWindows = false))
    }


    private fun quote(value: String, isWindows: Boolean): String = if (isWindows) {
        if (value.isEmpty()) "\"\"" else if (value.any { it.isWhitespace() || it == '"' }) {
            "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        } else {
            value
        }
    } else {
        if (value.isEmpty()) "''" else if (value.any { it.isWhitespace() || it in "'\\\"$`()[]{}*?&;|<>" }) {
            "'" + value.replace("'", "'\"'\"'") + "'"
        } else {
            value
        }
    }
}
