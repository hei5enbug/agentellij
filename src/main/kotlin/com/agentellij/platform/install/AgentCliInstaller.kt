package com.agentellij.platform.install

import com.agentellij.core.agent.AgentProfile
import com.agentellij.core.discovery.NodeBinDirectories
import com.agentellij.core.install.CancellationSignal
import com.agentellij.core.install.InstallOutcome
import com.agentellij.core.install.InstallProcess
import com.agentellij.core.install.InstallRunner
import com.agentellij.platform.env.RealSystemProbe
import com.agentellij.platform.env.currentPlatformIsWindows
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.io.InputStream
import java.util.concurrent.TimeUnit

object AgentCliInstaller {
    private val logger = Logger.getInstance(AgentCliInstaller::class.java)

    fun installWithUserConsent(
        project: Project,
        profile: AgentProfile,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val command = profile.buildInstallCommand(currentPlatformIsWindows())
        if (command == null) {
            onFailure("Automatic install is not available for ${profile.displayName}.")
            return
        }

        object : Task.Backgroundable(project, "Installing ${profile.displayName}", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Running ${profile.installCommandLabel ?: command.joinToString(" ")}"
                val result = AgentCliInstallRunner.run(command, indicator)
                if (result.cancelled) throw ProcessCanceledException()

                ApplicationManager.getApplication().invokeLater {
                    if (result.exitCode == 0) {
                        notifySuccess(project, profile)
                        onSuccess()
                    } else {
                        val detail = result.output.trim().takeIf { it.isNotEmpty() }
                            ?: "Installer exited with code ${result.exitCode}."
                        logger.warn("${profile.displayName} install failed: $detail")
                        notifyFailure(project, profile, detail)
                        onFailure(detail)
                    }
                }
            }
        }.queue()
    }

    private fun notifySuccess(project: Project, profile: AgentProfile) {
        Notification(
            "AgentellIJ",
            "${profile.displayName} installed",
            "Installation finished successfully.",
            NotificationType.INFORMATION
        ).notify(project)
    }

    private fun notifyFailure(project: Project, profile: AgentProfile, detail: String) {
        Notification(
            "AgentellIJ",
            "Failed to install ${profile.displayName}",
            escapeHtml(detail.take(1_000)),
            NotificationType.ERROR
        ).notify(project)
    }
}

/**
 * Connects the pure install runner to a real process and the IDE's progress indicator.
 */
internal object AgentCliInstallRunner {

    fun run(
        command: List<String>,
        indicator: ProgressIndicator,
        startProcess: (List<String>) -> Process = ::startProcess
    ): InstallOutcome = InstallRunner.run(
        startProcess = { RealInstallProcess(startProcess(command)) },
        cancellation = CancellationSignal { indicator.isCanceled },
        nowMillis = System::currentTimeMillis
    )

    private fun startProcess(command: List<String>): Process {
        val resolved = NodeBinDirectories.resolve(command, RealSystemProbe)
        val builder = ProcessBuilder(resolved.command).redirectErrorStream(true)
        resolved.path?.let { builder.environment()["PATH"] = it }
        return builder.start()
    }
}

internal class RealInstallProcess(private val process: Process) : InstallProcess {
    override val output: InputStream get() = process.inputStream
    override fun isAlive(): Boolean = process.isAlive
    override fun awaitExit(timeoutMillis: Long): Boolean = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
    override fun exitCode(): Int = process.exitValue()
    override fun destroy() = process.destroy()
    override fun destroyForcibly() {
        process.destroyForcibly()
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
