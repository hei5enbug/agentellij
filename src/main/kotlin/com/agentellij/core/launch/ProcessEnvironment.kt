package com.agentellij.core.launch

/**
 * Adjusts the search path a launched agent process inherits.
 *
 * An IDE started from the desktop inherits a minimal path that usually omits the
 * directories package managers install into, so those are put in front.
 */
internal object ProcessEnvironment {

    val HOMEBREW_DIRECTORIES = listOf("/opt/homebrew/bin", "/usr/local/bin")

    /**
     * Returns the path to hand the child process, or null when it already covers
     * everything and does not need changing.
     *
     * Two known defects are preserved here because changing either would alter which
     * binary an existing user's agent resolves to. A directory counts as present when it
     * appears anywhere in the string, so `/usr/local/bin-old` suppresses `/usr/local/bin`.
     * An absent path becomes a trailing empty entry, which POSIX reads as the working
     * directory.
     */
    fun pathWithHomebrew(currentPath: String?, separator: String): String? {
        val path = currentPath.orEmpty()
        val missing = HOMEBREW_DIRECTORIES.filter { !path.contains(it) }
        if (missing.isEmpty()) return null

        return (missing + path).joinToString(separator)
    }
}
