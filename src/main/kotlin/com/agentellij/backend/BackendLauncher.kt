package com.agentellij.backend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.agentellij.settings.AgentellIJSettings
import com.agentellij.util.resolveAbsolutePath
import java.io.PipedOutputStream

object BackendLauncher {
    private val logger = Logger.getInstance(BackendLauncher::class.java)

    fun launchBackend(project: Project): BackendProcess = launchBackend(project, AgentProfileResolver.resolve())

    fun launchBackend(project: Project, profile: AgentProfile): BackendProcess {
        require(!ApplicationManager.getApplication().isDispatchThread) {
            "launchBackend must not be called from EDT"
        }

        val bin = findAgentBinary(profile)
        val settings = AgentellIJSettings.getInstance()
        val customArgs = settings.state.customArgs.trim()

        val args = profile.buildLaunchArgs(bin, customArgs)
        if (customArgs.isNotEmpty()) {
            logger.info("Launching ${profile.displayName} backend with extra args: '$customArgs'")
        } else {
            logger.info("Launching ${profile.displayName} backend with default args")
        }

        val baseDir = project.basePath ?: System.getProperty("user.dir")
        val outputBuffer = PipedOutputStream()

        return try {
            launchDirect(args, baseDir, outputBuffer)
        } catch (e: Exception) {
            if (customArgs.isNotEmpty()) {
                logger.warn("Failed with custom args '$customArgs': ${e.message}, trying default")
                val fallbackArgs = profile.buildLaunchArgs(bin, "")
                launchDirect(fallbackArgs, baseDir, outputBuffer)
            } else {
                throw e
            }
        }
    }

    private fun launchDirect(
        args: List<String>,
        workingDir: String,
        outputBuffer: PipedOutputStream
    ): BackendProcess {
        val resolvedBin = resolveAbsolutePath(args.first())
        val resolvedArgs = listOf(resolvedBin) + args.drop(1)
        logger.info("Launching process: ${resolvedArgs.joinToString(" ")}")

        val pb = ProcessBuilder(resolvedArgs)
            .directory(java.io.File(workingDir))
            .redirectErrorStream(true)

        val env = pb.environment()
        val path = System.getenv("PATH") ?: ""
        val homebrewPaths = listOf("/opt/homebrew/bin", "/usr/local/bin")
        val extraPaths = homebrewPaths.filter { !path.contains(it) }
        if (extraPaths.isNotEmpty()) {
            env["PATH"] = (extraPaths + path).joinToString(java.io.File.pathSeparator)
        }

        val process = pb.start()
        logger.info("Backend process started (pid=${process.pid()})")
        return DirectBackendProcess(process, outputBuffer)
    }

    private fun findAgentBinary(profile: AgentProfile): String {
        val settings = AgentellIJSettings.getInstance()
        val settingsPath = settings.state.agentPath.trim()
        if (settingsPath.isNotEmpty() && java.io.File(settingsPath).canExecute()) return settingsPath

        val agentEnv = System.getenv("AGENTELLIJ_BIN")
        if (!agentEnv.isNullOrBlank() && java.io.File(agentEnv).canExecute()) return agentEnv

        for (envVar in profile.binaryEnvVars) {
            val envVal = System.getenv(envVar)
            if (!envVal.isNullOrBlank() && java.io.File(envVal).canExecute()) return envVal
        }
        return profile.defaultBinary
    }

}
