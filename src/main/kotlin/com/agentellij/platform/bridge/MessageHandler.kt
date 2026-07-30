package com.agentellij.platform.bridge

import com.agentellij.core.util.Diagnostics
import com.agentellij.core.bridge.BridgeRoutes
import com.agentellij.core.bridge.LineRange
import com.agentellij.core.bridge.OpenFileRequest
import com.agentellij.core.state.AgentStateStore
import com.agentellij.core.util.runQuietly
import com.agentellij.platform.env.IdeLoggerDiagnostics
import com.agentellij.platform.toolwindow.AgentellIJWiring
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

class MessageHandler(mapper: ObjectMapper) : BridgeRouteHandler {
    private val LOG by lazy { Logger.getInstance(MessageHandler::class.java) }
    private val diagnostics: Diagnostics by lazy { IdeLoggerDiagnostics(LOG) }
    private val stateStore = AgentStateStore(mapper, IdeLoggerDiagnostics(LOG))

    private val statePath: File?
        get() = AgentellIJWiring.agentStateDirectory()

    override fun handle(
        session: BridgeSession,
        project: Project?,
        type: String?,
        id: String?,
        payload: JsonNode?
    ) {
        when (type) {
            BridgeRoutes.OPEN_FILE -> handleOpenFile(session, project, id, payload)
            BridgeRoutes.OPEN_URL -> handleOpenUrl(session, id, payload)
            BridgeRoutes.RELOAD_PATH -> handleReloadPath(session, id, payload)
            BridgeRoutes.KV_GET -> handleKvGet(session, id)
            BridgeRoutes.KV_UPDATE -> handleKvUpdate(session, id, payload)
            BridgeRoutes.MODEL_GET -> handleModelGet(session, id)
            BridgeRoutes.MODEL_UPDATE -> handleModelUpdate(session, id, payload)
            BridgeRoutes.SETTINGS_GET -> handleSettingsGet(session, id)
            BridgeRoutes.SETTINGS_UPDATE -> handleSettingsUpdate(session, id, payload)
            else -> IdeBridge.replyError(session, id, BridgeRoutes.unknownTypeMessage(type))
        }
    }

    private fun handleOpenFile(session: BridgeSession, project: Project?, id: String?, payload: JsonNode?) {
        val rawPath = payload?.get("path")?.asText()
        if (rawPath == null) {
            IdeBridge.replyError(session, id, "Missing path")
            return
        }

        val target = OpenFileRequest.parse(rawPath, payload.get("line")?.asInt() ?: -1)

        if (project != null) openFile(project, target.path, target.startLine, target.endLine)
        IdeBridge.replyOk(session, id)
    }

    private fun openFile(project: Project, rawPath: String, startLine: Int, endLine: Int) {
        try {
            val vf = findVirtualFile(rawPath) ?: return
            ApplicationManager.getApplication().invokeLater {
                val fm = FileEditorManager.getInstance(project)
                if (startLine < 0) {
                    fm.openFile(vf, true)
                    return@invokeLater
                }
                val editor = openFileAtLine(project, fm, vf, startLine)
                if (editor != null) {
                    selectRange(editor, startLine, endLine)
                }
            }
        } catch (t: Throwable) {
            LOG.warn("openFile failed", t)
        }
    }

    private fun findVirtualFile(rawPath: String): VirtualFile? {
        val lfs = LocalFileSystem.getInstance()
        return lfs.findFileByPath(rawPath) ?: lfs.refreshAndFindFileByPath(rawPath)
    }

    private fun openFileAtLine(project: Project, fm: FileEditorManager, vf: VirtualFile, startLine: Int): Editor? =
        runQuietly(diagnostics, "describe the file to open") { OpenFileDescriptor(project, vf, startLine, 0) }?.let { desc ->
            runQuietly(diagnostics, "reuse the current editor window") { desc.isUseCurrentWindow = true }
            runQuietly(diagnostics, "open the file in an editor") { fm.openTextEditor(desc, true) }
        } ?: run {
            fm.openFile(vf, true)
            null
        }

    private fun selectRange(editor: Editor, startLine: Int, endLine: Int) {
        runQuietly(diagnostics, "select the requested line range") {
            val doc = editor.document
            val range = LineRange.clamp(startLine, endLine, doc.lineCount)

            editor.caretModel.moveToLogicalPosition(LogicalPosition(range.startLine, 0))

            if (range.selects) {
                val startOffset = doc.getLineStartOffset(range.startLine)
                val endOffset = doc.getLineEndOffset(range.endLine)
                editor.selectionModel.setSelection(startOffset, endOffset)
            } else {
                editor.selectionModel.removeSelection()
            }

            editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
        }
    }

    private fun handleOpenUrl(session: BridgeSession, id: String?, payload: JsonNode?) {
        val url = payload?.get("url")?.asText()
        if (url != null) {
            BrowserUtil.browse(url)
            IdeBridge.replyOk(session, id)
        } else {
            IdeBridge.replyError(session, id, "Missing url")
        }
    }

    private fun handleReloadPath(session: BridgeSession, id: String?, payload: JsonNode?) {
        val path = payload?.get("path")?.asText()
        if (path == null) {
            IdeBridge.replyError(session, id, "Missing path")
            return
        }
        try {
            val lfs = LocalFileSystem.getInstance()
            val vf = lfs.findFileByPath(path) ?: lfs.refreshAndFindFileByPath(path)
            if (vf != null) {
                vf.refresh(true, false)
            } else {
                val parentPath = path.substringBeforeLast("/")
                val parentVf = lfs.findFileByPath(parentPath) ?: lfs.refreshAndFindFileByPath(parentPath)
                parentVf?.refresh(true, true)
            }
        } catch (t: Throwable) {
            LOG.warn("reloadPath failed", t)
        }
        IdeBridge.replyOk(session, id)
    }

    // --- KV Store ---

    private fun handleKvGet(session: BridgeSession, id: String?) {
        IdeBridge.replyWithPayload(session, id, stateStore.getKv(statePath))
    }

    private fun handleKvUpdate(session: BridgeSession, id: String?, payload: JsonNode?) {
        IdeBridge.replyWithPayload(session, id, stateStore.updateKv(statePath, payload))
    }

    // --- Model Store ---

    private fun handleModelGet(session: BridgeSession, id: String?) {
        IdeBridge.replyWithPayload(session, id, stateStore.getModel(statePath))
    }

    private fun handleModelUpdate(session: BridgeSession, id: String?, payload: JsonNode?) {
        IdeBridge.replyWithPayload(session, id, stateStore.updateModel(statePath, payload))
    }

    // --- Settings Store ---

    private fun handleSettingsGet(session: BridgeSession, id: String?) {
        IdeBridge.replyWithPayload(session, id, stateStore.getSettings(statePath))
    }

    private fun handleSettingsUpdate(session: BridgeSession, id: String?, payload: JsonNode?) {
        IdeBridge.replyWithPayload(session, id, stateStore.updateSettings(statePath, payload))
    }
}
