package com.agentellij.util

/** Close without throwing. Covers Closeable, HttpExchange, and any AutoCloseable. */
fun AutoCloseable?.closeQuietly() {
    try { this?.close() } catch (_: Throwable) {}
}

/** Run [block] and return its result, or null on any exception. */
inline fun <T> runQuietly(block: () -> T): T? =
    try { block() } catch (_: Throwable) { null }

/** Resolve a binary name to its absolute path via PATH lookup. Returns the input if already absolute or not found. */
fun resolveAbsolutePath(binary: String): String {
    if (java.io.File(binary).isAbsolute) return binary
    val isWin = System.getProperty("os.name").lowercase().contains("win")
    val cmd = if (isWin) listOf("where", binary) else listOf("which", binary)
    return runQuietly {
        val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val result = proc.inputStream.bufferedReader().readLine()?.trim()
        proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        if (!result.isNullOrEmpty() && java.io.File(result).exists()) result else binary
    } ?: binary
}
