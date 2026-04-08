package com.agentellij.backend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.agentellij.settings.AgentellIJSettings
import com.agentellij.util.resolveAbsolutePath
import java.io.PipedOutputStream

object BackendLauncher {
    private val logger = Logger.getInstance(BackendLauncher::class.java)

    const val MODE_GUI = "gui"
    const val MODE_TUI = "tui"

    fun launchBackend(project: Project): BackendProcess = launchBackend(project, AgentProfileResolver.resolve())

    fun launchBackend(project: Project, profile: AgentProfile): BackendProcess {
        require(!ApplicationManager.getApplication().isDispatchThread) {
            "launchBackend must not be called from EDT"
        }

        val settings = AgentellIJSettings.getInstance()
        val customArgs = settings.state.customArgs.trim()

        val args = buildLaunchCommand(profile, MODE_GUI, customArgs)
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
                val fallbackArgs = buildLaunchCommand(profile, MODE_GUI, "")
                launchDirect(fallbackArgs, baseDir, outputBuffer)
            } else {
                throw e
            }
        }
    }

    fun buildLaunchCommand(mode: String): List<String> {
        val profile = AgentProfileResolver.resolve()
        val customArgs = AgentellIJSettings.getInstance().state.customArgs.trim()
        return buildLaunchCommand(profile, mode, customArgs)
    }

    private fun launchDirect(
        args: List<String>,
        workingDir: String,
        outputBuffer: PipedOutputStream
    ): BackendProcess {
        val resolvedBin = resolveAbsolutePath(args.first())
        val resolvedArgs = listOf(resolvedBin) + args.drop(1)
        logger.info("Launching process: ${resolvedArgs.joinToString(" ")}")

        val process = try {
            createProcess(resolvedArgs, workingDir)
        } catch (e: java.io.IOException) {
            logger.warn("Failed to create process: ${e.message}, attempting recovery")
            tryRecover(resolvedArgs, workingDir) ?: throw e
        }

        logger.info("Backend process started (pid=${process.pid()})")
        return DirectBackendProcess(process, outputBuffer)
    }

    private fun createProcess(args: List<String>, workingDir: String): Process {
        val pb = ProcessBuilder(args)
            .directory(java.io.File(workingDir))
            .redirectErrorStream(true)

        val env = pb.environment()
        val path = System.getenv("PATH") ?: ""
        val homebrewPaths = listOf("/opt/homebrew/bin", "/usr/local/bin")
        val extraPaths = homebrewPaths.filter { !path.contains(it) }
        if (extraPaths.isNotEmpty()) {
            env["PATH"] = (extraPaths + path).joinToString(java.io.File.pathSeparator)
        }

        return pb.start()
    }

    private fun tryRecover(args: List<String>, workingDir: String): Process? {
        val binaryPath = args.firstOrNull() ?: return null
        val binaryFile = java.io.File(binaryPath)

        // Case 1: Binary exists but not executable
        if (binaryFile.exists() && !binaryFile.canExecute()) {
            logger.info("Binary exists but not executable, setting executable: $binaryPath")
            if (!binaryFile.setExecutable(true)) {
                logger.warn("Failed to set executable permission on: $binaryPath")
                return null
            }
            return try {
                createProcess(args, workingDir)
            } catch (e: java.io.IOException) {
                logger.warn("Retry after chmod failed: ${e.message}")
                null
            }
        }

        // Case 2: Binary not found, scan common paths
        if (!binaryFile.exists()) {
            val binaryName = binaryFile.name
            val altPath = scanCommonPaths(binaryName)
            if (altPath != null) {
                logger.info("Found alternative binary at: $altPath")
                val altArgs = listOf(altPath) + args.drop(1)
                return try {
                    createProcess(altArgs, workingDir)
                } catch (e: java.io.IOException) {
                    logger.warn("Retry with scanned path failed: ${e.message}")
                    null
                }
            }
        }

        return null
    }

    private fun scanCommonPaths(binaryName: String): String? {
        val home = System.getProperty("user.home") ?: return null
        val nvmDir = System.getenv("NVM_DIR") ?: "$home/.nvm"

        val searchPaths = listOf(
            "$home/.local/bin",
            "$home/.npm-global/bin",
            "$nvmDir/current/bin"
        )

        for (dir in searchPaths) {
            val candidate = java.io.File(dir, binaryName)
            if (candidate.exists() && candidate.canExecute()) {
                return candidate.absolutePath
            }
        }
        return null
    }

    private fun buildLaunchCommand(
        profile: AgentProfile,
        mode: String,
        customArgs: String
    ): List<String> = profile.buildLaunchArgs(resolveBinary(profile), customArgs, mode)

    private fun resolveBinary(profile: AgentProfile): String {
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
