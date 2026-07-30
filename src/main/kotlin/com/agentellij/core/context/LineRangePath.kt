package com.agentellij.core.context

/**
 * The `path:start-end` notation used to hand a selected range to the agent.
 *
 * This module owns the format. The bridge reads it back when the agent asks to open the
 * file again, so the writer and the reader have to agree; a round-trip test keeps them
 * honest.
 *
 * Line numbers here are one-based, matching what a person reads in the editor gutter.
 */
internal object LineRangePath {

    fun format(path: String, startLine: Int, endLine: Int): String = "$path:$startLine-$endLine"

    /**
     * The offset to look the last line up at.
     *
     * A selection ends just past its last character, so looking the line up at the raw
     * end offset would report the following line whenever the selection stops at a line
     * break.
     */
    fun lastLineLookupOffset(selectionEnd: Int): Int = (selectionEnd - 1).coerceAtLeast(0)
}
