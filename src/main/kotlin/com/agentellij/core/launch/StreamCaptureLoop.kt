package com.agentellij.core.launch

import com.agentellij.core.util.Diagnostics
import java.io.InputStream

/**
 * Copies an agent's output stream into a sink until there is a reason to stop.
 *
 * Getting the exit conditions wrong here is expensive and hard to notice: a loop that
 * will not end keeps a thread and a process alive after the tool window is gone, and
 * the symptom is a slow IDE rather than an error. The conditions are therefore kept in
 * one place and exercised directly.
 */
internal enum class CaptureOutcome {
    /** The source ended, the sink closed, or the caller asked to stop. */
    FINISHED,

    /** The reading thread was interrupted; the caller must restore its own flag. */
    INTERRUPTED,

    /** Reading failed and the failure was reported. */
    FAILED
}

internal object StreamCaptureLoop {
    private const val CHUNK_SIZE = 4096

    /**
     * @param shouldContinue checked before every read; false ends the loop.
     * @param sink receives each chunk. A failure here ends the loop, because a closed
     *   pipe means nobody is listening any more.
     */
    fun run(
        source: InputStream,
        shouldContinue: () -> Boolean,
        sink: (ByteArray, Int) -> Unit,
        diagnostics: Diagnostics
    ): CaptureOutcome {
        val chunk = ByteArray(CHUNK_SIZE)
        try {
            while (shouldContinue()) {
                val read = source.read(chunk)
                if (read == -1) break

                try {
                    sink(chunk, read)
                } catch (_: Exception) {
                    break
                }
            }
        } catch (_: InterruptedException) {
            // The interrupt flag belongs to the thread, which the caller owns.
            return CaptureOutcome.INTERRUPTED
        } catch (e: Exception) {
            if (shouldContinue()) diagnostics.warn("Capture error", e)
            return CaptureOutcome.FAILED
        }
        return CaptureOutcome.FINISHED
    }
}

/**
 * Turns repeated snapshots of a terminal screen into just the text that is new.
 *
 * The terminal is polled rather than streamed, so each snapshot repeats everything seen
 * so far. A snapshot that got shorter means the screen was cleared or scrolled away;
 * nothing is emitted then, because re-sending old text would confuse the reader.
 */
internal class IncrementalTextCapture {
    private var lastLength = 0

    /** Returns the newly appended text, or null when there is nothing new. */
    fun nextChunk(text: String?): String? {
        if (text == null || text.length <= lastLength) return null

        val chunk = text.substring(lastLength)
        lastLength = text.length
        return chunk
    }
}
