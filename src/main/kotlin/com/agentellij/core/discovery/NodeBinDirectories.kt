package com.agentellij.core.discovery


/**
 * Resolves npm-based install commands to an absolute executable path and an augmented PATH.
 *
 * GUI-launched IDEs on macOS/Linux inherit a minimal launchd/systemd PATH that omits the dirs
 * where Node and npm live (Homebrew, nvm, Volta, asdf). On Unix the JVM resolves a bare program
 * name using the *parent* process PATH, not the child env, so `ProcessBuilder(listOf("npm", ...))`
 * fails with `Cannot run program "npm"`. The fix is to resolve npm to an absolute path and prepend
 * the Node bin dir to the child PATH so npm can still locate `node`.
 */
internal object NodeBinDirectories {
    data class ResolvedCommand(val command: List<String>, val path: String?)

    fun resolve(command: List<String>, probe: SystemProbe): ResolvedCommand {
        // Windows inherits the registry PATH and runs through cmd.exe, which is always found.
        if (command.isEmpty() || probe.isWindows) return ResolvedCommand(command, null)

        val binDirs = nodeBinDirs(probe)
        val program = command.first()
        val resolvedDir = if (program.contains('/')) {
            null
        } else {
            binDirs.firstOrNull { dir -> probe.isExecutable("$dir/$program") }
        }

        val resolvedCommand =
            if (resolvedDir != null) listOf("$resolvedDir/$program") + command.drop(1) else command
        val pathDirs = listOfNotNull(resolvedDir) + binDirs
        return ResolvedCommand(resolvedCommand, buildPath(pathDirs, probe::env, probe.pathSeparator))
    }

    fun nodeBinDirs(probe: SystemProbe): List<String> =
        nodeBinDirsFrom(probe.userHome, probe::env, probe::childNames, probe::fileLines)

    internal fun nodeBinDirsFrom(
        userHome: String,
        env: (String) -> String?,
        listChildren: (String) -> List<String>,
        readFileLines: (String) -> List<String>
    ): List<String> {
        val dirs = LinkedHashSet<String>()
        dirs.addAll(nvmVersionBinDirs(userHome, env, listChildren))
        dirs.addAll(fnmVersionBinDirs(userHome, env, listChildren))
        npmConfigPrefixBinDir(env)?.let { dirs.add(it) }
        npmrcPrefixBinDir(userHome, env, readFileLines)?.let { dirs.add(it) }
        dirs.add("$userHome/.volta/bin")
        dirs.add("$userHome/.asdf/shims")
        dirs.add("$userHome/.npm-global/bin")
        dirs.add("$userHome/.local/bin")
        dirs.add("/opt/homebrew/bin")
        dirs.add("/home/linuxbrew/.linuxbrew/bin")
        dirs.add("$userHome/.linuxbrew/bin")
        dirs.add("/opt/local/bin")
        dirs.add("/usr/local/bin")
        dirs.add("/usr/bin")
        dirs.add("/snap/bin")
        return dirs.filter { it.isNotBlank() }
    }

    internal fun nvmVersionBinDirs(
        userHome: String,
        env: (String) -> String?,
        listChildren: (String) -> List<String>
    ): List<String> {
        val nvmDir = env("NVM_DIR")?.trim().orEmpty().ifBlank { "$userHome/.nvm" }
        if (nvmDir.isBlank()) return emptyList()
        val nodeRoot = "$nvmDir/versions/node"
        return listChildren(nodeRoot)
            .filter { it.isNotBlank() }
            .sortedWith(newestVersionFirst)
            .map { version -> "$nodeRoot/$version/bin" }
    }

    internal fun fnmVersionBinDirs(
        userHome: String,
        env: (String) -> String?,
        listChildren: (String) -> List<String>
    ): List<String> {
        val bases = LinkedHashSet<String>()
        env("FNM_DIR")?.trim()?.takeIf { it.isNotBlank() }?.let { bases.add(it) }
        bases.add("$userHome/.fnm")
        val xdgData = env("XDG_DATA_HOME")?.trim().orEmpty()
        bases.add(if (xdgData.isNotBlank()) "$xdgData/fnm" else "$userHome/.local/share/fnm")
        bases.add("$userHome/Library/Application Support/fnm")
        env("LOCALAPPDATA")?.trim()?.takeIf { it.isNotBlank() }?.let { bases.add("$it/fnm") }

        // fnm stores each node version under <base>/node-versions/<version>/installation/bin.
        return bases.flatMap { base ->
            val versionsRoot = "$base/node-versions"
            listChildren(versionsRoot)
                .filter { it.isNotBlank() }
                .sortedWith(newestVersionFirst)
                .map { version -> "$versionsRoot/$version/installation/bin" }
        }
    }

    private fun npmConfigPrefixBinDir(env: (String) -> String?): String? {
        val prefix = (env("NPM_CONFIG_PREFIX") ?: env("npm_config_prefix"))
            ?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return "${prefix.trimEnd('/')}/bin"
    }

    private fun npmrcPrefixBinDir(
        userHome: String,
        env: (String) -> String?,
        readFileLines: (String) -> List<String>
    ): String? {
        val userConfig = (env("NPM_CONFIG_USERCONFIG") ?: env("npm_config_userconfig"))
            ?.trim()?.takeIf { it.isNotBlank() } ?: "$userHome/.npmrc"
        val rawPrefix = readFileLines(userConfig)
            .asReversed()
            .firstNotNullOfOrNull(::parsePrefixLine) ?: return null
        val expanded = expandConfigValue(rawPrefix, userHome, env)
        return expanded.takeIf { it.isNotBlank() }?.let { "${it.trimEnd('/')}/bin" }
    }

    private fun parsePrefixLine(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#")) return null
        val eq = trimmed.indexOf('=')
        if (eq <= 0 || !trimmed.substring(0, eq).trim().equals("prefix", ignoreCase = true)) return null
        var value = trimmed.substring(eq + 1).trim()
        if (value.length >= 2 &&
            ((value.first() == '"' && value.last() == '"') || (value.first() == '\'' && value.last() == '\''))
        ) {
            value = value.substring(1, value.length - 1)
        }
        return value.takeIf { it.isNotBlank() }
    }

    // npm expands ${VAR} from the environment in .npmrc values; ~ is expanded for convenience.
    private fun expandConfigValue(value: String, userHome: String, env: (String) -> String?): String {
        val expanded = ENV_VAR_PATTERN.replace(value) { match ->
            val name = match.groupValues[1]
            if (name == "HOME") userHome else env(name).orEmpty()
        }
        return when {
            expanded == "~" -> userHome
            expanded.startsWith("~/") -> userHome + expanded.substring(1)
            else -> expanded
        }
    }

    private val ENV_VAR_PATTERN = Regex("""\$\{([^}]+)}""")


    private fun buildPath(prependDirs: List<String>, env: (String) -> String?, separator: String): String {
        val ordered = LinkedHashSet<String>()
        prependDirs.forEach { if (it.isNotBlank()) ordered.add(it) }
        env("PATH").orEmpty().split(separator).forEach {
            if (it.isNotBlank()) ordered.add(it)
        }
        return ordered.joinToString(separator)
    }


    // Orders node version dir names ("v20.11.1", "18.19.0") by numeric segments, newest first.
    private val newestVersionFirst = Comparator<String> { a, b ->
        val ka = versionKey(a)
        val kb = versionKey(b)
        val size = maxOf(ka.size, kb.size)
        for (i in 0 until size) {
            val diff = kb.getOrElse(i) { 0 } - ka.getOrElse(i) { 0 }
            if (diff != 0) return@Comparator diff
        }
        b.compareTo(a)
    }

    private fun versionKey(name: String): List<Int> =
        name.trimStart('v', 'V')
            .split('.', '-', '+', '_')
            .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
}
