package com.agentellij.backend

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TerminalShellCommandTest {
    @Test
    fun `posix render quotes empty whitespace metacharacters and single quotes`() {
        val rendered = TerminalShellCommand.renderInner(
            listOf("opencode", "", "two words", "has'quote", "dollar$", "plain"),
            isWindows = false
        )

        assertEquals("opencode '' 'two words' 'has'\"'\"'quote' 'dollar$' plain", rendered)
    }

    @Test
    fun `windows render quotes empty whitespace and double quotes`() {
        val rendered = TerminalShellCommand.renderInner(
            listOf("opencode", "", "two words", "has\"quote", "C:\\Tools\\opencode"),
            isWindows = true
        )

        assertEquals("opencode \"\" \"two words\" \"has\\\"quote\" C:\\Tools\\opencode", rendered)
    }

    @Test
    fun `unix wrap uses executable SHELL as login interactive command shell`() {
        val wrapped = TerminalShellCommand.wrap(
            command = listOf("opencode", "--model", "gpt 5"),
            isWindows = false,
            env = { if (it == "SHELL") "/opt/homebrew/bin/zsh" else null },
            isExecutable = { it == "/opt/homebrew/bin/zsh" },
            fileExists = { false }
        )

        assertEquals(listOf("/opt/homebrew/bin/zsh", "-l", "-i", "-c", "opencode --model 'gpt 5'"), wrapped)
    }

    @Test
    fun `unix wrap falls back to first existing standard shell`() {
        val wrapped = TerminalShellCommand.wrap(
            command = listOf("claude"),
            isWindows = false,
            env = { if (it == "SHELL") "/missing/shell" else null },
            isExecutable = { false },
            fileExists = { it == "/bin/bash" }
        )

        assertEquals(listOf("/bin/bash", "-l", "-i", "-c", "claude"), wrapped)
    }

    @Test
    fun `unix wrap falls back to bin sh when no candidate exists`() {
        val wrapped = TerminalShellCommand.wrap(
            command = listOf("codex"),
            isWindows = false,
            env = { null },
            isExecutable = { false },
            fileExists = { false }
        )

        assertEquals(listOf("/bin/sh", "-l", "-i", "-c", "codex"), wrapped)
    }

    @Test
    fun `windows wrap uses ComSpec and keeps terminal open`() {
        val wrapped = TerminalShellCommand.wrap(
            command = listOf("opencode", "two words"),
            isWindows = true,
            env = { if (it == "ComSpec") "C:\\Windows\\System32\\cmd.exe" else null },
            isExecutable = { false },
            fileExists = { false }
        )

        assertEquals(listOf("C:\\Windows\\System32\\cmd.exe", "/k", "opencode \"two words\""), wrapped)
    }

    @Test
    fun `windows wrap falls back to cmd exe`() {
        val wrapped = TerminalShellCommand.wrap(
            command = listOf("opencode"),
            isWindows = true,
            env = { null },
            isExecutable = { false },
            fileExists = { false }
        )

        assertEquals(listOf("cmd.exe", "/k", "opencode"), wrapped)
    }
}
