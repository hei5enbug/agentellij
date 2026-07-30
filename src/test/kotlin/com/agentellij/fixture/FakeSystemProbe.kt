package com.agentellij.fixture

import com.agentellij.core.discovery.SystemProbe

/**
 * A machine described entirely by the test.
 *
 * Nothing here touches the real environment, so a spec behaves the same on a developer
 * laptop and on a build agent.
 */
class FakeSystemProbe(
    override val isWindows: Boolean = false,
    override val pathSeparator: String = if (isWindows) ";" else ":",
    override val userHome: String = "/home/me",
    private val variables: Map<String, String> = emptyMap(),
    private val directories: Map<String, List<String>> = emptyMap(),
    private val files: Map<String, List<String>> = emptyMap(),
    private val executables: Set<String> = emptySet()
) : SystemProbe {

    override fun env(name: String): String? = variables[name]

    override fun childNames(directory: String): List<String> = directories[directory].orEmpty()

    override fun fileLines(path: String): List<String> = files[path].orEmpty()

    override fun isExecutable(path: String): Boolean = path in executables
}
