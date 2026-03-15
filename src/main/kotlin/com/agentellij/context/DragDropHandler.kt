package com.agentellij.context

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.ui.jcef.JBCefBrowser
import org.cef.browser.CefBrowser
import org.cef.callback.CefDragData
import org.cef.handler.CefDragHandler
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File

object DragDropHandler {
    fun install(project: Project, browser: JBCefBrowser, logger: Logger) {
        browser.cefBrowser.client?.addDragHandler(object : CefDragHandler {
            override fun onDragEnter(
                browser: CefBrowser?,
                dragData: CefDragData?,
                mask: Int
            ): Boolean = false
        })

        val component = browser.component
        component.dropTarget = DropTarget(component, DnDConstants.ACTION_COPY_OR_MOVE, object : DropTargetAdapter() {
            override fun drop(event: DropTargetDropEvent) {
                try {
                    event.acceptDrop(DnDConstants.ACTION_COPY)
                    val transferable = event.transferable
                    val paths = mutableListOf<String>()

                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        val files = transferable.getTransferData(DataFlavor.javaFileListFlavor)
                        if (files is List<*>) {
                            files.filterIsInstance<File>().forEach { file ->
                                paths.add(file.absolutePath)
                            }
                        }
                    }

                    if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                        val text = transferable.getTransferData(DataFlavor.stringFlavor) as? String
                        if (text != null && paths.isEmpty()) {
                            text.lines().filter { it.isNotBlank() }.forEach { line ->
                                val trimmed = line.trim()
                                if (File(trimmed).exists()) paths.add(trimmed)
                            }
                        }
                    }

                    if (paths.isNotEmpty()) {
                        ContextSender.insertPaths(project, paths)
                    }
                    event.dropComplete(true)
                } catch (e: Exception) {
                    logger.warn("Drop failed", e)
                    event.dropComplete(false)
                }
            }
        })
    }
}
