package com.agentellij.platform.toolwindow

import com.agentellij.platform.surface.GuiModeContent
import com.agentellij.platform.surface.TuiModeContent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import java.util.concurrent.atomic.AtomicReference

/**
 * The tool window entry point, and the place the object graph is assembled.
 *
 * Each mode owns a disposable of its own, registered under the tool window's. Swapping
 * modes disposes the outgoing one first, so a surface never outlives the screen it drew.
 */
class ChatToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val modeDisposable = AtomicReference<Disposable?>(null)

        fun installMode(requestedMode: String) {
            val profile = AgentellIJWiring.activeProfile()
            val mode = AgentellIJWiring.applyMode(requestedMode, profile)

            modeDisposable.getAndSet(null)?.let { Disposer.dispose(it) }
            toolWindow.contentManager.contents.toList().forEach {
                toolWindow.contentManager.removeContent(it, true)
            }

            val surfaceDisposable = Disposer.newDisposable("agentellij-mode-$mode")
            Disposer.register(toolWindow.disposable, surfaceDisposable)
            modeDisposable.set(surfaceDisposable)

            when (mode) {
                TERMINAL_MODE -> TuiModeContent(project, toolWindow, surfaceDisposable, profile).install()
                else -> GuiModeContent(project, toolWindow, surfaceDisposable, profile).install()
            }
        }

        toolWindow.setTitleActions(AgentToolbarActions.create(::installMode))
        installMode(AgentellIJWiring.currentMode())
    }

    private companion object {
        const val TERMINAL_MODE = "tui"
    }
}
