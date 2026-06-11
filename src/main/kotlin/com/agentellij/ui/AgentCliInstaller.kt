package com.agentellij.ui

import com.agentellij.backend.AgentProfile
import com.agentellij.backend.NodeCliResolver
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object AgentCliInstaller {
    private val logger = Logger.getInstance(AgentCliInstaller::class.java)

    fun installWithUserConsent(
        project: Project,
        profile: AgentProfile,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val command = profile.buildInstallCommand()
        if (command == null) {
            onFailure("Automatic install is not available for ${profile.displayName}.")
            return
        }

        object : Task.Backgroundable(project, "Installing ${profile.displayName}", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Running ${profile.installCommandLabel ?: command.joinToString(" ")}"
                val result = AgentCliInstallRunner.run(command, ProgressIndicatorCancellationChecker(indicator))
                ApplicationManager.getApplication().invokeLater {
                    if (result.exitCode == 0) {
                        Notification(
                            "AgentellIJ",
                            "${profile.displayName} installed",
                            "Installation finished successfully.",
                            NotificationType.INFORMATION
                        ).notify(project)
                        onSuccess()
                    } else {
                        val detail = result.output.trim().takeIf { it.isNotEmpty() }
                            ?: "Installer exited with code ${result.exitCode}."
                        logger.warn("${profile.displayName} install failed: $detail")
                        Notification(
                            "AgentellIJ",
                            "Failed to install ${profile.displayName}",
                            escapeHtml(detail.take(1_000)),
                            NotificationType.ERROR
                        ).notify(project)
                        onFailure(detail)
                    }
                }
            }
        }.queue()
    }
}

internal object AgentCliInstallRunner {
    private const val OUTPUT_LIMIT_BYTES = 64 * 1024
    private const val TIMEOUT_MINUTES = 10L

    fun run(
        command: List<String>,
        cancellationChecker: CancellationChecker,
        startProcess: (List<String>) -> Process = ::startProcess
    ): InstallResult {
        return try {
            val process = startProcess(command)
            val output = ByteArrayOutputStream()
            val outputThread = Thread {
                process.inputStream.use { input ->
                    val buffer = ByteArray(4096)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        synchronized(output) {
                            val remaining = OUTPUT_LIMIT_BYTES - output.size()
                            if (remaining > 0) output.write(buffer, 0, minOf(read, remaining))
                        }
                    }
                }
            }
            outputThread.isDaemon = true
            outputThread.start()

            val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(TIMEOUT_MINUTES)
            try {
                while (process.isAlive) {
                    cancellationChecker.checkCanceled()
                    if (System.nanoTime() >= deadline) {
                        destroyProcess(process)
                        return InstallResult(-1, "Installer timed out after $TIMEOUT_MINUTES minutes.")
                    }
                    process.waitFor(250, TimeUnit.MILLISECONDS)
                }
            } catch (e: ProcessCanceledException) {
                destroyProcess(process)
                throw e
            }
            outputThread.join(1_000)
            InstallResult(process.exitValue(), synchronized(output) { output.toString(Charsets.UTF_8) })
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            InstallResult(-1, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun startProcess(command: List<String>): Process {
        val resolved = NodeCliResolver.resolve(command)
        val builder = ProcessBuilder(resolved.command).redirectErrorStream(true)
        resolved.path?.let { builder.environment()["PATH"] = it }
        return builder.start()
    }

    private fun destroyProcess(process: Process) {
        process.destroy()
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly()
        }
    }
}

internal data class InstallResult(val exitCode: Int, val output: String)

internal interface CancellationChecker {
    fun checkCanceled()
}

internal class ProgressIndicatorCancellationChecker(
    private val indicator: ProgressIndicator
) : CancellationChecker {
    override fun checkCanceled() {
        indicator.checkCanceled()
    }
}

internal fun escapeHtml(value: String): String = buildString(value.length) {
    for (ch in value) {
        append(
            when (ch) {
                '<' -> "&lt;"
                '>' -> "&gt;"
                '&' -> "&amp;"
                '"' -> "&quot;"
                else -> ch
            }
        )
    }
}
