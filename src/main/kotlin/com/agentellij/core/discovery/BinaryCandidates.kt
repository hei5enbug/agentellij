package com.agentellij.core.discovery

/** One place worth checking for an agent binary. */
internal data class BinaryCandidate(val directory: String, val fileName: String)

/**
 * Builds the ordered list of places an agent binary might be, without looking at any
 * of them.
 *
 * Separating "where to look" from "looking" is what makes the search order testable.
 * Order matters: an agent present in more than one location should be picked up from
 * the one the user configured rather than whichever the filesystem answers first.
 */
internal object BinaryCandidates {

    private val WINDOWS_SUFFIXES = listOf(".exe", ".cmd", ".bat", "")
    private val UNIX_SUFFIXES = listOf("")

    /**
     * @param nodeBinDirectories directories produced by the node tooling search, which
     *   is where npm-installed agents end up.
     */
    fun candidatesFor(
        binaryName: String,
        isWindows: Boolean,
        userHome: String,
        env: (String) -> String?,
        nodeBinDirectories: List<String>
    ): List<BinaryCandidate> {
        val suffixes = if (isWindows) WINDOWS_SUFFIXES else UNIX_SUFFIXES

        return searchDirectories(userHome, env, nodeBinDirectories).flatMap { directory ->
            suffixes.map { suffix -> BinaryCandidate(directory, "$binaryName$suffix") }
        }
    }

    private fun searchDirectories(
        userHome: String,
        env: (String) -> String?,
        nodeBinDirectories: List<String>
    ): List<String> {
        val nvmDir = env("NVM_DIR").orEmpty().ifBlank { "$userHome/.nvm" }
        val codexHome = env("CODEX_HOME").orEmpty().ifBlank { "$userHome/.codex" }
        val userProfile = env("USERPROFILE").orEmpty().ifBlank { userHome }
        val localAppData = env("LOCALAPPDATA").orEmpty()

        return (
            listOf(
                env("OPENCODE_INSTALL_DIR").orEmpty(),
                env("CODEX_INSTALL_DIR").orEmpty(),
                env("XDG_BIN_DIR").orEmpty(),
                "$userHome/bin",
                "$userHome/.opencode/bin",
                "$codexHome/packages/standalone/current"
            ) +
                nodeBinDirectories +
                listOf(
                    "$nvmDir/current/bin",
                    "$localAppData/Programs/OpenAI/Codex/bin",
                    "${env("APPDATA").orEmpty()}/npm",
                    "$localAppData/npm",
                    "$userProfile/scoop/shims",
                    "C:/ProgramData/chocolatey/bin"
                )
            ).distinct().filter { it.isNotBlank() }
    }
}
