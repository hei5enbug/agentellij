package com.agentellij.core.context

/** The three ways something can be added to the agent context. */
enum class ContextActionKind { LINES, FILE, DIRECTORY }

/**
 * Decides which context action a shared shortcut should run.
 *
 * All three actions answer the same keystroke, so the one that matches where the user is
 * looking has to win. In the editor the most specific choice is the selected lines; in
 * the project tree it is the tree selection.
 */
internal object ContextActionOrder {

    private val EDITOR_ORDER = listOf(ContextActionKind.LINES, ContextActionKind.FILE, ContextActionKind.DIRECTORY)
    private val TREE_ORDER = listOf(ContextActionKind.DIRECTORY, ContextActionKind.FILE, ContextActionKind.LINES)

    fun order(editorFocused: Boolean): List<ContextActionKind> = if (editorFocused) EDITOR_ORDER else TREE_ORDER

    /**
     * Sorts our own candidates to the front and leaves everything else behind them.
     *
     * Returns an empty list when none of the candidates are ours, which tells the IDE we
     * have no opinion about this keystroke.
     */
    fun <T> promote(candidates: List<T>, editorFocused: Boolean, kindOf: (T) -> ContextActionKind?): List<T> {
        val ranking = order(editorFocused).withIndex().associate { (index, kind) -> kind to index }
        val ours = candidates.filter { kindOf(it) != null }
        if (ours.isEmpty()) return emptyList()

        val sorted = ours.sortedBy { ranking[kindOf(it)] ?: Int.MAX_VALUE }
        return sorted + candidates.filter { candidate -> ours.none { it === candidate } }
    }
}
