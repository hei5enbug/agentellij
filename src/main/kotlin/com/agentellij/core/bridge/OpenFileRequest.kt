package com.agentellij.core.bridge

/**
 * Where an `openFile` request wants the editor to land.
 *
 * Line numbers are zero-based here because that is what the editor takes. A value of
 * -1 means "not specified".
 */
internal data class OpenFileTarget(val path: String, val startLine: Int, val endLine: Int)

/**
 * Understands the `path` field of an `openFile` request.
 *
 * The path may carry its own range suffix, written as `path:12` or `path:12-20`.
 * That suffix is produced by the editor action that adds selected lines to the agent
 * context, so the two sides have to agree on the format.
 */
internal object OpenFileRequest {
    private val RANGE_SUFFIX = Regex(":(\\d+)(?:-(\\d+))?$")

    /**
     * @param lineFromPayload one-based line sent alongside the path, or -1 when absent.
     *   A positive value takes precedence over a suffix in the path.
     */
    fun parse(rawPath: String, lineFromPayload: Int): OpenFileTarget {
        val match = RANGE_SUFFIX.find(rawPath)
        val startFromPath = match?.groupValues?.getOrNull(1)?.toIntOrNull()
        val endFromPath = match?.groupValues?.getOrNull(2)?.toIntOrNull()
        val cleanedPath = rawPath.replace(RANGE_SUFFIX, "")

        val startOneBased = if (lineFromPayload > 0) lineFromPayload else startFromPath ?: -1
        val endOneBased = endFromPath ?: -1

        return OpenFileTarget(
            path = cleanedPath,
            startLine = if (startOneBased > 0) startOneBased - 1 else -1,
            endLine = if (endOneBased > 0) endOneBased - 1 else -1
        )
    }
}
