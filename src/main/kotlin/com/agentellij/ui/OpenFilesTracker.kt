package com.agentellij.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.agentellij.bridge.IdeBridge
import com.agentellij.util.runQuietly
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class OpenFilesTracker(
    private val project: Project,
    private val sessionId: String
) : Disposable {
    private val logger = Logger.getInstance(OpenFilesTracker::class.java)
    private var scheduled: ScheduledFuture<*>? = null

    fun install() {
        val bus = project.messageBus.connect(this)

        bus.subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) { pushAsync() }
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) { pushAsync() }
                override fun fileClosed(source: FileEditorManager, file: VirtualFile) { pushAsync() }
            }
        )

        scheduled = AppExecutorUtil.getAppScheduledExecutorService()
            .scheduleWithFixedDelay({ pushAsync() }, 2, 5, TimeUnit.SECONDS)
    }

    private fun pushAsync() {
        AppExecutorUtil.getAppExecutorService().execute {
            try {
                val openFiles = mutableListOf<VirtualFile>()
                var selectedFile: VirtualFile? = null

                val latch = CountDownLatch(1)
                ApplicationManager.getApplication().invokeLater {
                    try {
                        val fem = FileEditorManager.getInstance(project)
                        openFiles.addAll(fem.openFiles)
                        selectedFile = fem.selectedEditor?.file
                    } finally {
                        latch.countDown()
                    }
                }

                try { latch.await() } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@execute
                }
                if (project.isDisposed) return@execute

                val opened = openFiles.mapNotNull { vf -> toRelativePath(vf) }
                val current = selectedFile?.let { vf -> toRelativePath(vf) }

                IdeBridge.send(sessionId, "updateOpenedFiles", mapOf(
                    "openedFiles" to opened,
                    "currentFile" to current
                ))
            } catch (e: Exception) {
                logger.warn("Failed to push open files", e)
            }
        }
    }

    private fun toRelativePath(vf: VirtualFile?): String? {
        if (vf == null) return null
        val projBase = project.basePath
            ?: return runQuietly { vf.toNioPath().toAbsolutePath().normalize().toString() } ?: vf.path

        return runQuietly {
            val filePath = vf.toNioPath().toAbsolutePath().normalize()
            val base = Paths.get(projBase).toAbsolutePath().normalize()
            val rel = if (filePath.startsWith(base)) base.relativize(filePath) else filePath
            rel.toString().ifEmpty { vf.name }
        } ?: fallbackRelativePath(vf, projBase)
    }

    private fun fallbackRelativePath(vf: VirtualFile, projBase: String): String {
        val abs = runQuietly { vf.toNioPath().toAbsolutePath().normalize().toString() } ?: vf.path
        return runQuietly {
            val base = java.io.File(projBase).absoluteFile.normalize().path
            val rel = if (abs.startsWith(base + java.io.File.separator)) abs.substring(base.length + 1) else abs
            rel.ifEmpty { vf.name }
        } ?: abs
    }

    override fun dispose() {
        runQuietly { scheduled?.cancel(false) }
        scheduled = null
    }
}
