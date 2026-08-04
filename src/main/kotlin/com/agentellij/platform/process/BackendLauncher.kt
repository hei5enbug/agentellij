package com.agentellij.platform.process

import com.agentellij.core.agent.AgentProfile
import com.agentellij.core.launch.BinaryResolver
import com.agentellij.core.launch.LaunchCommandPlanner
import com.agentellij.core.launch.ProcessEnvironment
import com.agentellij.core.launch.ProcessRecovery
import com.agentellij.core.launch.RecoveryPlan
import com.agentellij.core.launch.TuiLaunchPlan
import com.agentellij.core.launch.TuiLaunchPlanner
import com.agentellij.platform.env.BackendBinaryDiscovery
import com.agentellij.platform.env.resolveAbsolutePath
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.File
import java.io.IOException
import java.io.PipedOutputStream

object BackendLauncher {
    private val logger = Logger.getInstance(BackendLauncher::class.java)

    const val MODE_GUI = "gui"

    fun launchBackend(
        project: Project,
        profile: AgentProfile,
        settingsPath: String,
        customArgs: String
    ): BackendProcess {
        require(!ApplicationManager.getApplication().isDispatchThread) {
            "launchBackend must not be called from EDT"
        }

        val normalizedSettingsPath = settingsPath.trim()
        val normalizedCustomArgs = customArgs.trim()
        val attempts = LaunchCommandPlanner.attempts(
            profile = profile,
            mode = MODE_GUI,
            binary = resolveBinary(profile, normalizedSettingsPath),
            customArgs = normalizedCustomArgs
        )
        if (normalizedCustomArgs.isNotEmpty()) {
            logger.info("Launching ${profile.displayName} backend with extra args: '$normalizedCustomArgs'")
        } else {
            logger.info("Launching ${profile.displayName} backend with default args")
        }

        val baseDir = project.basePath ?: System.getProperty("user.dir")
        val outputBuffer = PipedOutputStream()

        return launchFirstThatStarts(attempts, baseDir, outputBuffer, normalizedCustomArgs)
    }

    private fun launchFirstThatStarts(
        attempts: List<List<String>>,
        baseDir: String,
        outputBuffer: PipedOutputStream,
        customArgs: String
    ): BackendProcess {
        attempts.dropLast(1).forEach { args ->
            try {
                return launchDirect(args, baseDir, outputBuffer)
            } catch (e: Exception) {
                logger.warn("Failed with custom args '$customArgs': ${e.message}, trying default")
            }
        }

        return launchDirect(attempts.last(), baseDir, outputBuffer)
    }

    fun buildLaunchCommand(
        profile: AgentProfile,
        mode: String,
        settingsPath: String,
        customArgs: String
    ): List<String> = buildLaunchCommandInternal(profile, mode, settingsPath.trim(), customArgs.trim())

    fun buildTuiLaunchPlan(
        profile: AgentProfile,
        settingsPath: String,
        customArgs: String,
        agentellijBin: String? = System.getenv("AGENTELLIJ_BIN")
    ): TuiLaunchPlan =
        TuiLaunchPlanner.plan(
            profile = profile,
            settingsPath = settingsPath.trim(),
            customArgs = customArgs.trim(),
            agentellijBin = agentellijBin,
            agentSpecificEnv = { System.getenv(it) },
            discoverBinary = BackendBinaryDiscovery::discoverBinary,
            canExecute = { File(it).canExecute() }
        )

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
        } catch (e: IOException) {
            logger.warn("Failed to create process: ${e.message}, attempting recovery")
            tryRecover(resolvedArgs, workingDir) ?: throw e
        }

        logger.info("Backend process started (pid=${process.pid()})")
        return DirectBackendProcess(process, outputBuffer)
    }

    private fun createProcess(args: List<String>, workingDir: String): Process {
        val pb = ProcessBuilder(args)
            .directory(File(workingDir))
            .redirectErrorStream(true)

        ProcessEnvironment.pathWithHomebrew(System.getenv("PATH"), File.pathSeparator)?.let { pb.environment()["PATH"] = it }

        return pb.start()
    }

    private fun tryRecover(args: List<String>, workingDir: String): Process? {
        val binaryPath = args.firstOrNull()
        val binaryFile = binaryPath?.let { File(it) }
        val exists = binaryFile?.exists() == true

        val recovery = RecoveryPlan.decide(
            binaryPath = binaryPath,
            exists = exists,
            canExecute = binaryFile?.canExecute() == true,
            alternative = if (binaryFile != null && !exists) {
                BackendBinaryDiscovery.scanCommonPaths(binaryFile.name)
            } else {
                null
            }
        )

        return when (recovery) {
            is ProcessRecovery.GiveUp -> null

            is ProcessRecovery.SetExecutableAndRetry -> {
                logger.info("Binary exists but not executable, setting executable: $binaryPath")
                if (binaryFile?.setExecutable(true) != true) {
                    logger.warn("Failed to set executable permission on: $binaryPath")
                    null
                } else {
                    retry(args, workingDir, "Retry after chmod failed")
                }
            }

            is ProcessRecovery.RetryWithAlternative -> {
                logger.info("Found alternative binary at: ${recovery.path}")
                retry(listOf(recovery.path) + args.drop(1), workingDir, "Retry with scanned path failed")
            }
        }
    }

    private fun retry(args: List<String>, workingDir: String, failureMessage: String): Process? = try {
        createProcess(args, workingDir)
    } catch (e: IOException) {
        logger.warn("$failureMessage: ${e.message}")
        null
    }

    private fun buildLaunchCommandInternal(
        profile: AgentProfile,
        mode: String,
        settingsPath: String,
        customArgs: String
    ): List<String> = profile.buildLaunchArgs(resolveBinary(profile, settingsPath), customArgs, mode)

    private fun resolveBinary(profile: AgentProfile, settingsPath: String): String =
        BinaryResolver.resolve(
            profile = profile,
            settingsPath = settingsPath,
            agentellijBin = System.getenv("AGENTELLIJ_BIN"),
            agentSpecificEnv = { envVar -> System.getenv(envVar) },
            discoverBinary = BackendBinaryDiscovery::discoverBinary,
            canExecute = { path -> File(path).canExecute() },
            onDiscovered = { discoveredBinary ->
                logger.info("Auto-detected ${profile.displayName} binary at: $discoveredBinary")
            }
        )
}
