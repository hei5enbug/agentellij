package com.agentellij.bridge

import com.agentellij.util.runQuietly
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

class MessageHandler(private val mapper: ObjectMapper) {
    private val LOG by lazy { Logger.getInstance(MessageHandler::class.java) }
    private val profile by lazy { com.agentellij.backend.AgentProfileResolver.resolve() }

    private val statePath: File?
        get() = profile.statePath

    fun handle(session: BridgeSession, type: String?, id: String?, payload: JsonNode?) {
        when (type) {
            "openFile" -> handleOpenFile(session, id, payload)
            "openUrl" -> handleOpenUrl(session, id, payload)
            "reloadPath" -> handleReloadPath(session, id, payload)
            "kv.get" -> handleKvGet(session, id)
            "kv.update" -> handleKvUpdate(session, id, payload)
            "model.get" -> handleModelGet(session, id)
            "model.update" -> handleModelUpdate(session, id, payload)
            "settings.get" -> handleSettingsGet(session, id)
            "settings.update" -> handleSettingsUpdate(session, id, payload)
            else -> IdeBridge.replyError(session, id, "Unknown type: $type")
        }
    }

    private fun handleOpenFile(session: BridgeSession, id: String?, payload: JsonNode?) {
        val rawPath = payload?.get("path")?.asText()
        if (rawPath == null) {
            IdeBridge.replyError(session, id, "Missing path")
            return
        }

        val lineFromPayload = payload.get("line")?.asInt() ?: -1
        val rangeRegex = Regex(":(\\d+)(?:-(\\d+))?$")
        val match = rangeRegex.find(rawPath)
        val startFromPath = runQuietly { match?.groupValues?.getOrNull(1)?.toInt() }
        val endFromPath = runQuietly { match?.groupValues?.getOrNull(2)?.toInt() }
        val cleanedPath = rawPath.replace(rangeRegex, "")

        val startLine1Based = if (lineFromPayload > 0) lineFromPayload else startFromPath ?: -1
        val endLine1Based = endFromPath ?: -1

        val startLine0Based = if (startLine1Based > 0) startLine1Based - 1 else -1
        val endLine0Based = if (endLine1Based > 0) endLine1Based - 1 else -1

        openFile(session.project, cleanedPath, startLine0Based, endLine0Based)
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
        runQuietly { OpenFileDescriptor(project, vf, startLine, 0) }?.let { desc ->
            runQuietly { desc.isUseCurrentWindow = true }
            runQuietly { fm.openTextEditor(desc, true) }
        } ?: run {
            fm.openFile(vf, true)
            null
        }

    private fun selectRange(editor: Editor, startLine: Int, endLine: Int) {
        runQuietly {
            val doc = editor.document
            val lineCount = doc.lineCount
            val clampedStart = startLine.coerceIn(0, (lineCount - 1).coerceAtLeast(0))
            val targetEnd = if (endLine >= 0) endLine else startLine
            val clampedEnd = targetEnd.coerceIn(clampedStart, (lineCount - 1).coerceAtLeast(0))

            editor.caretModel.moveToLogicalPosition(LogicalPosition(clampedStart.coerceAtLeast(0), 0))

            if (clampedEnd > clampedStart) {
                val startOffset = doc.getLineStartOffset(clampedStart)
                val endOffset = doc.getLineEndOffset(clampedEnd)
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
        val dir = statePath
        if (dir == null) { IdeBridge.replyWithPayload(session, id, mapper.createObjectNode()); return }
        val data = readJsonObject(File(dir, "kv.json"))
        IdeBridge.replyWithPayload(session, id, data)
    }

    private fun handleKvUpdate(session: BridgeSession, id: String?, payload: JsonNode?) {
        val dir = statePath
        if (dir == null) { IdeBridge.replyWithPayload(session, id, mapper.createObjectNode()); return }
        val file = File(dir, "kv.json")
        val existing = readJsonObject(file)
        payload?.fields()?.forEach { (k, v) -> existing.set<JsonNode>(k, v) }
        dir.mkdirs()
        file.writeText(mapper.writeValueAsString(existing))
        IdeBridge.replyWithPayload(session, id, existing)
    }

    // --- Model Store ---

    private fun handleModelGet(session: BridgeSession, id: String?) {
        val dir = statePath
        if (dir == null) { IdeBridge.replyWithPayload(session, id, emptyModelData()); return }
        val data = readModelData(File(dir, "model.json"))
        IdeBridge.replyWithPayload(session, id, data)
    }

    private fun handleModelUpdate(session: BridgeSession, id: String?, payload: JsonNode?) {
        val dir = statePath
        if (dir == null) { IdeBridge.replyWithPayload(session, id, emptyModelData()); return }
        val file = File(dir, "model.json")
        val existing = readModelData(file)
        if (payload?.has("recent") == true) existing.set<JsonNode>("recent", payload.get("recent"))
        if (payload?.has("favorite") == true) existing.set<JsonNode>("favorite", payload.get("favorite"))
        if (payload?.has("variant") == true) {
            val current = existing.get("variant") as? ObjectNode ?: mapper.createObjectNode()
            payload.get("variant").fields().forEach { (k, v) -> current.set<JsonNode>(k, v) }
            existing.set<JsonNode>("variant", current)
        }
        dir.mkdirs()
        file.writeText(mapper.writeValueAsString(existing))
        IdeBridge.replyWithPayload(session, id, existing)
    }

    private fun readModelData(file: File): ObjectNode {
        val raw = readJsonObject(file)
        return mapper.createObjectNode().apply {
            set<JsonNode>("recent", if (raw.has("recent") && raw.get("recent").isArray) raw.get("recent") else mapper.createArrayNode())
            set<JsonNode>("favorite", if (raw.has("favorite") && raw.get("favorite").isArray) raw.get("favorite") else mapper.createArrayNode())
            set<JsonNode>("variant", if (raw.has("variant") && raw.get("variant").isObject) raw.get("variant") else mapper.createObjectNode())
        }
    }

    private fun emptyModelData(): ObjectNode = mapper.createObjectNode().apply {
        set<JsonNode>("recent", mapper.createArrayNode())
        set<JsonNode>("favorite", mapper.createArrayNode())
        set<JsonNode>("variant", mapper.createObjectNode())
    }

    // --- Settings Store ---

    private fun handleSettingsGet(session: BridgeSession, id: String?) {
        val data = readSettings()
        IdeBridge.replyWithPayload(session, id, data)
    }

    private fun handleSettingsUpdate(session: BridgeSession, id: String?, payload: JsonNode?) {
        val dir = statePath
        if (dir == null) { IdeBridge.replyWithPayload(session, id, normalizeSettings(mapper.createObjectNode())); return }
        val current = readSettings()
        payload?.fields()?.forEach { (k, v) -> current.set<JsonNode>(k, v) }
        val normalized = normalizeSettings(current)
        dir.mkdirs()
        File(dir, "settings.json").writeText(mapper.writeValueAsString(normalized))
        IdeBridge.replyWithPayload(session, id, normalized)
    }

    private fun readSettings(): ObjectNode {
        val dir = statePath ?: return normalizeSettings(mapper.createObjectNode())
        return normalizeSettings(readJsonObject(File(dir, "settings.json")))
    }

    private fun normalizeSettings(raw: ObjectNode): ObjectNode {
        val normalized = raw.deepCopy()
        if (normalized.has("theme")) {
            val theme = normalized.get("theme")
            val valid = theme != null && theme.isTextual && (theme.asText() == "light" || theme.asText() == "dark")
            if (!valid) normalized.remove("theme")
        }
        return normalized
    }

    // --- Helpers ---

    private fun readJsonObject(file: File): ObjectNode {
        return try {
            if (file.exists()) {
                val tree = mapper.readTree(file.readText())
                if (tree is ObjectNode) tree else mapper.createObjectNode()
            } else {
                mapper.createObjectNode()
            }
        } catch (_: Throwable) {
            mapper.createObjectNode()
        }
    }
}
