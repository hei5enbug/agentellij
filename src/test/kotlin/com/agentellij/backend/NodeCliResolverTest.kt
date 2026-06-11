package com.agentellij.backend

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class NodeCliResolverTest {
    private val sep = File.pathSeparator

    @Test
    fun `resolves npm to absolute homebrew path and preserves arguments`() {
        val resolved = NodeCliResolver.resolve(
            command = listOf("npm", "install", "-g", "@openai/codex"),
            isWindows = false,
            env = { if (it == "PATH") "/usr/bin" else null },
            userHome = "/home/me",
            isExecutable = { it == "/opt/homebrew/bin/npm" },
            listChildren = { emptyList() }
        )

        assertEquals(
            listOf("/opt/homebrew/bin/npm", "install", "-g", "@openai/codex"),
            resolved.command
        )
        assertTrue(resolved.path!!.startsWith("/opt/homebrew/bin"))
    }

    @Test
    fun `prefers newest nvm node version when multiple have npm`() {
        val nodeRoot = "/home/me/.nvm/versions/node"
        val resolved = NodeCliResolver.resolve(
            command = listOf("npm", "install", "-g", "@openai/codex"),
            isWindows = false,
            env = { if (it == "PATH") "/usr/bin" else null },
            userHome = "/home/me",
            isExecutable = { it == "$nodeRoot/v20.11.1/bin/npm" || it == "$nodeRoot/v18.19.0/bin/npm" },
            listChildren = { dir ->
                if (dir == nodeRoot) listOf("v18.19.0", "v20.11.1", "v16.20.0") else emptyList()
            }
        )

        assertEquals("$nodeRoot/v20.11.1/bin/npm", resolved.command.first())
    }

    @Test
    fun `windows command is left untouched`() {
        val command = listOf("cmd", "/c", "npm", "install", "-g", "@openai/codex")
        val resolved = NodeCliResolver.resolve(
            command = command,
            isWindows = true,
            env = { null },
            userHome = "C:/Users/me",
            isExecutable = { true },
            listChildren = { emptyList() }
        )

        assertEquals(command, resolved.command)
        assertNull(resolved.path)
    }

    @Test
    fun `keeps bare command but still augments PATH when npm is not found`() {
        val resolved = NodeCliResolver.resolve(
            command = listOf("npm", "install", "-g", "opencode-ai"),
            isWindows = false,
            env = { if (it == "PATH") "/usr/bin" else null },
            userHome = "/home/me",
            isExecutable = { false },
            listChildren = { emptyList() }
        )

        assertEquals(listOf("npm", "install", "-g", "opencode-ai"), resolved.command)
        val entries = resolved.path!!.split(File.pathSeparatorChar)
        assertTrue(entries.contains("/opt/homebrew/bin"))
        assertTrue(entries.contains("/usr/bin"))
    }

    @Test
    fun `does not rewrite an explicit executable path`() {
        val resolved = NodeCliResolver.resolve(
            command = listOf("/custom/bin/npm", "install", "-g", "opencode-ai"),
            isWindows = false,
            env = { if (it == "PATH") "/usr/bin" else null },
            userHome = "/home/me",
            isExecutable = { true },
            listChildren = { emptyList() }
        )

        assertEquals("/custom/bin/npm", resolved.command.first())
    }

    @Test
    fun `augmented PATH puts resolved dir first and dedups existing entries`() {
        val resolved = NodeCliResolver.resolve(
            command = listOf("npm", "install", "-g", "opencode-ai"),
            isWindows = false,
            env = { if (it == "PATH") "/usr/bin${sep}/opt/homebrew/bin" else null },
            userHome = "/home/me",
            isExecutable = { it == "/opt/homebrew/bin/npm" },
            listChildren = { emptyList() }
        )

        val entries = resolved.path!!.split(File.pathSeparatorChar)
        assertEquals("/opt/homebrew/bin", entries.first())
        assertEquals(1, entries.count { it == "/opt/homebrew/bin" })
        assertTrue(entries.contains("/usr/bin"))
    }

    @Test
    fun `nvm version dirs are newest first and honor NVM_DIR`() {
        val dirs = NodeCliResolver.nvmVersionBinDirs(
            userHome = "/home/me",
            env = { if (it == "NVM_DIR") "/opt/nvm" else null },
            listChildren = { dir ->
                if (dir == "/opt/nvm/versions/node") listOf("v18.19.0", "v20.11.1") else emptyList()
            }
        )

        assertEquals(
            listOf("/opt/nvm/versions/node/v20.11.1/bin", "/opt/nvm/versions/node/v18.19.0/bin"),
            dirs
        )
    }

    @Test
    fun `nvm version dirs default to home nvm directory`() {
        val dirs = NodeCliResolver.nvmVersionBinDirs(
            userHome = "/home/me",
            env = { null },
            listChildren = { dir ->
                if (dir == "/home/me/.nvm/versions/node") listOf("v20.11.1") else emptyList()
            }
        )

        assertEquals(listOf("/home/me/.nvm/versions/node/v20.11.1/bin"), dirs)
    }

    @Test
    fun `resolves npm from newest fnm version honoring FNM_DIR`() {
        val versionsRoot = "/opt/fnm/node-versions"
        val resolved = NodeCliResolver.resolve(
            command = listOf("npm", "install", "-g", "@openai/codex"),
            isWindows = false,
            env = { if (it == "FNM_DIR") "/opt/fnm" else null },
            userHome = "/home/me",
            isExecutable = { it == "$versionsRoot/v20.11.1/installation/bin/npm" },
            listChildren = { dir ->
                if (dir == versionsRoot) listOf("v18.20.0", "v20.11.1") else emptyList()
            }
        )

        assertEquals("$versionsRoot/v20.11.1/installation/bin/npm", resolved.command.first())
    }

    @Test
    fun `fnm version dirs are newest first under installation bin`() {
        val dirs = NodeCliResolver.fnmVersionBinDirs(
            userHome = "/home/me",
            env = { if (it == "FNM_DIR") "/opt/fnm" else null },
            listChildren = { dir ->
                if (dir == "/opt/fnm/node-versions") listOf("v18.20.0", "v20.11.1") else emptyList()
            }
        )

        assertEquals(
            listOf(
                "/opt/fnm/node-versions/v20.11.1/installation/bin",
                "/opt/fnm/node-versions/v18.20.0/installation/bin",
            ),
            dirs
        )
    }

    @Test
    fun `resolves npm from custom npm config prefix`() {
        val resolved = NodeCliResolver.resolve(
            command = listOf("npm", "install", "-g", "opencode-ai"),
            isWindows = false,
            env = {
                when (it) {
                    "PATH" -> "/usr/bin"
                    "NPM_CONFIG_PREFIX" -> "/opt/npm-prefix/"
                    else -> null
                }
            },
            userHome = "/home/me",
            isExecutable = { it == "/opt/npm-prefix/bin/npm" },
            listChildren = { emptyList() }
        )

        assertEquals("/opt/npm-prefix/bin/npm", resolved.command.first())
    }

    @Test
    fun `resolves npm installed via snap`() {
        val resolved = NodeCliResolver.resolve(
            command = listOf("npm", "install", "-g", "opencode-ai"),
            isWindows = false,
            env = { if (it == "PATH") "/usr/bin" else null },
            userHome = "/home/me",
            isExecutable = { it == "/snap/bin/npm" },
            listChildren = { emptyList() }
        )

        assertEquals("/snap/bin/npm", resolved.command.first())
    }

    @Test
    fun `node bin dirs include linuxbrew macports and snap`() {
        val dirs = NodeCliResolver.nodeBinDirs(
            userHome = "/home/me",
            env = { null },
            listChildren = { emptyList() }
        )

        assertTrue(dirs.contains("/home/linuxbrew/.linuxbrew/bin"))
        assertTrue(dirs.contains("/home/me/.linuxbrew/bin"))
        assertTrue(dirs.contains("/opt/local/bin"))
        assertTrue(dirs.contains("/snap/bin"))
    }

    @Test
    fun `node bin dirs include prefix from npmrc file`() {
        val dirs = NodeCliResolver.nodeBinDirs(
            userHome = "/home/me",
            env = { null },
            listChildren = { emptyList() },
            readFileLines = { path -> if (path == "/home/me/.npmrc") listOf("prefix=/opt/custom") else emptyList() }
        )

        assertTrue(dirs.contains("/opt/custom/bin"))
    }

    @Test
    fun `npmrc prefix expands HOME strips quotes and last entry wins`() {
        val dirs = NodeCliResolver.nodeBinDirs(
            userHome = "/home/me",
            env = { null },
            listChildren = { emptyList() },
            readFileLines = {
                listOf("prefix=/early", "; a comment", "prefix=\"\${HOME}/.npm-global\"")
            }
        )

        assertTrue(dirs.contains("/home/me/.npm-global/bin"))
        assertTrue(dirs.none { it == "/early/bin" })
    }

    @Test
    fun `npmrc prefix expands leading tilde`() {
        val dirs = NodeCliResolver.nodeBinDirs(
            userHome = "/home/me",
            env = { null },
            listChildren = { emptyList() },
            readFileLines = { listOf("prefix = ~/node-global") }
        )

        assertTrue(dirs.contains("/home/me/node-global/bin"))
    }

    @Test
    fun `env prefix takes precedence over npmrc prefix`() {
        val dirs = NodeCliResolver.nodeBinDirs(
            userHome = "/home/me",
            env = { if (it == "NPM_CONFIG_PREFIX") "/env-prefix" else null },
            listChildren = { emptyList() },
            readFileLines = { listOf("prefix=/rc-prefix") }
        )

        assertTrue(dirs.indexOf("/env-prefix/bin") in 0 until dirs.indexOf("/rc-prefix/bin"))
    }

    @Test
    fun `npmrc location honors NPM_CONFIG_USERCONFIG`() {
        val dirs = NodeCliResolver.nodeBinDirs(
            userHome = "/home/me",
            env = { if (it == "NPM_CONFIG_USERCONFIG") "/etc/custom-npmrc" else null },
            listChildren = { emptyList() },
            readFileLines = { path -> if (path == "/etc/custom-npmrc") listOf("prefix=/opt/x") else emptyList() }
        )

        assertTrue(dirs.contains("/opt/x/bin"))
    }
}
