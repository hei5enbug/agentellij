package com.agentellij.platform.env

import com.intellij.openapi.diagnostic.Logger
import com.agentellij.platform.env.IdeLoggerDiagnostics
import com.agentellij.core.discovery.BinaryCandidates
import com.agentellij.core.discovery.NodeBinDirectories
import com.agentellij.core.util.runQuietly
import java.io.File

/**
 * Resolve a binary name to its absolute path via PATH lookup.
 *
 * Returns the input unchanged when it is already absolute, when the lookup finds
 * nothing, or when the lookup fails.
 *
 * Known limitation, carried over deliberately: reading the lookup output has no time
 * limit, so a lookup command that never writes anything can block. The five second wait
 * that follows only bounds the process after its output stream has already closed.
 */
private val diagnostics = IdeLoggerDiagnostics(Logger.getInstance("com.agentellij.platform.env.PathLookup"))

fun resolveAbsolutePath(binary: String): String {
    if (File(binary).isAbsolute) return binary
    val command = if (currentPlatformIsWindows()) listOf("where", binary) else listOf("which", binary)
    return runQuietly(diagnostics, "look up $binary on the search path") {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val result = process.inputStream.bufferedReader().readLine()?.trim()
        process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        if (!result.isNullOrEmpty() && File(result).exists()) result else binary
    } ?: binary
}

/**
 * Finds agent binaries by asking the operating system about each candidate that
 * [BinaryCandidates] proposes.
 *
 * This object only probes; the order it probes in is decided elsewhere.
 */
internal object BackendBinaryDiscovery {

    fun discoverBinary(binaryName: String): String? {
        val resolvedFromPath = resolveAbsolutePath(binaryName)
        if (resolvedFromPath != binaryName) {
            val resolvedFile = File(resolvedFromPath)
            if (resolvedFile.exists() && resolvedFile.canExecute()) {
                return resolvedFile.absolutePath
            }
        }

        return scanCommonPaths(binaryName)
    }

    fun scanCommonPaths(binaryName: String): String? {
        val userHome = System.getProperty("user.home").orEmpty()
        val candidates = BinaryCandidates.candidatesFor(
            binaryName = binaryName,
            isWindows = currentPlatformIsWindows(),
            userHome = userHome,
            env = { System.getenv(it) },
            nodeBinDirectories = NodeBinDirectories.nodeBinDirs(RealSystemProbe)
        )

        return candidates
            .asSequence()
            .map { File(it.directory, it.fileName) }
            .firstOrNull { it.exists() && it.canExecute() }
            ?.absolutePath
    }
}
