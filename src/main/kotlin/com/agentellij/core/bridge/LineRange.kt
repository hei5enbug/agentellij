package com.agentellij.core.bridge

/** A line range that is guaranteed to fit inside the document it will be applied to. */
internal data class ClampedLineRange(val startLine: Int, val endLine: Int, val selects: Boolean)

/**
 * Fits a requested line range onto a document.
 *
 * A request can name lines that no longer exist, for example after the file shrank, so
 * the range is trimmed rather than trusted.
 */
internal object LineRange {

    fun clamp(startLine: Int, endLine: Int, lineCount: Int): ClampedLineRange {
        val lastLine = (lineCount - 1).coerceAtLeast(0)
        val clampedStart = startLine.coerceIn(0, lastLine)
        val requestedEnd = if (endLine >= 0) endLine else startLine
        val clampedEnd = requestedEnd.coerceIn(clampedStart, lastLine)

        return ClampedLineRange(
            startLine = clampedStart,
            endLine = clampedEnd,
            selects = clampedEnd > clampedStart
        )
    }
}
