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
import com.agentellij.context.ProjectPathResolver
import com.agentellij.util.DebouncedTask
import com.agentellij.util.runQuietly
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class OpenFilesTracker(
    private val project: Project,
    private val sessionId: String
) : Disposable {
    private val logger = Logger.getInstance(OpenFilesTracker::class.java)
    private var scheduled: ScheduledFuture<*>? = null
    private val debouncedPush = DebouncedTask(
        AppExecutorUtil.getAppScheduledExecutorService(),
        EVENT_DEBOUNCE_MS
    ) { pushAsync() }

    fun install() {
        val bus = project.messageBus.connect(this)

        bus.subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) { debouncedPush.request() }
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) { debouncedPush.request() }
                override fun fileClosed(source: FileEditorManager, file: VirtualFile) { debouncedPush.request() }
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
        return ProjectPathResolver.relativePath(vf, project.basePath)
    }

    override fun dispose() {
        debouncedPush.cancel()
        runQuietly { scheduled?.cancel(false) }
        scheduled = null
    }

    private companion object {
        const val EVENT_DEBOUNCE_MS = 250L
    }
}
