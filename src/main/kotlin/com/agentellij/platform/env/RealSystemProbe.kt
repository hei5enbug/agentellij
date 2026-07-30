package com.agentellij.platform.env

import com.agentellij.core.discovery.SystemProbe
import java.io.File

/**
 * Answers the discovery rules' questions by actually asking the machine.
 *
 * Every method absorbs its own failure and reports "nothing here" instead, because a
 * directory the user does not have, or a file the IDE cannot read, is an ordinary
 * situation during a search rather than an error.
 */
object RealSystemProbe : SystemProbe {

    override val isWindows: Boolean get() = currentPlatformIsWindows()

    override val pathSeparator: String get() = File.pathSeparator

    override val userHome: String get() = System.getProperty("user.home").orEmpty()

    override fun env(name: String): String? = System.getenv(name)

    override fun childNames(directory: String): List<String> =
        File(directory).listFiles()?.map { it.name }.orEmpty()

    override fun fileLines(path: String): List<String> =
        runCatching { File(path).takeIf { it.isFile }?.readLines().orEmpty() }.getOrDefault(emptyList())

    override fun isExecutable(path: String): Boolean = File(path).canExecute()
}
