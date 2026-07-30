package com.agentellij.core.discovery

/**
 * What the discovery rules need to know about the machine they are running on.
 *
 * Every one of these is a question about the outside world, so none of them can be
 * answered inside the pure layer. Grouping them keeps call sites readable: without it,
 * the node tooling search would take five separate lambdas.
 */
interface SystemProbe {
    val isWindows: Boolean

    /** What separates entries in the host's search path. */
    val pathSeparator: String

    val userHome: String

    fun env(name: String): String?

    /** Names of the entries directly inside [directory]; empty when it cannot be read. */
    fun childNames(directory: String): List<String>

    /** Lines of the file at [path]; empty when it does not exist or cannot be read. */
    fun fileLines(path: String): List<String>

    fun isExecutable(path: String): Boolean
}
