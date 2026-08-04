package com.agentellij.platform.process

import com.agentellij.core.agent.AgentCompletionHooks
import com.agentellij.platform.bridge.IdeBridge
import com.agentellij.platform.env.currentPlatformIsWindows
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.StandardCopyOption

/**
 * Owns the completion adapters for one terminal surface.
 *
 * The fixed-size adapters live under the IDE system directory, while the real binary
 * paths and authenticated callback live only in that terminal's environment. Disposing
 * the mode invalidates the bridge token, so another terminal cannot inherit the feature.
 */
internal class TuiCompletionRuntime private constructor(
    val environment: Map<String, String>,
    val shellActivationCommand: String?,
    private val wrappers: Map<String, Path>,
    private val bridgeSessionId: String
) : Disposable {

    fun wrapCommand(agentId: String, command: List<String>): List<String> {
        val wrapper = wrappers[agentId] ?: return command
        return listOf(wrapper.toString()) + command.drop(1)
    }

    override fun dispose() {
        IdeBridge.removeSession(bridgeSessionId)
    }

    companion object {
        private val LOG = Logger.getInstance(TuiCompletionRuntime::class.java)
        private val mapper = jacksonObjectMapper()

        fun create(
            project: Project,
            agentBinaries: Map<String, String>,
            inheritedPath: String?,
            inheritedOpenCodeConfig: String?,
            adapterDirectory: Path = Path.of(PathManager.getSystemPath(), "agentellij", "completion"),
            isWindows: Boolean = currentPlatformIsWindows()
        ): TuiCompletionRuntime {
            val session = IdeBridge.createSession(project)
            try {
                val files = ensureAdapterFiles(adapterDirectory, isWindows)
                val notifier = files.notifier

                val baseWrappers = buildMap {
                    agentBinaries["codex"]?.let {
                        put("codex", files.codexWrapper)
                    }
                    agentBinaries["claude"]?.let {
                        put("claude", files.claudeWrapper)
                    }
                }

                val openCodeConfig = AgentCompletionHooks.withOpenCodePlugin(
                    inheritedOpenCodeConfig,
                    files.openCodePlugin.toUri().toString(),
                    mapper
                )
                if (openCodeConfig == null) {
                    LOG.warn("OpenCode inline config is malformed; completion notifications are disabled for OpenCode")
                }
                val wrappers = if (openCodeConfig != null && agentBinaries.containsKey("opencode")) {
                    baseWrappers + ("opencode" to files.openCodeWrapper)
                } else {
                    baseWrappers
                }

                val path = listOfNotNull(
                    adapterDirectory.toString().takeIf { wrappers.isNotEmpty() },
                    inheritedPath?.takeIf { it.isNotBlank() }
                ).joinToString(File.pathSeparator)
                val environment = buildMap {
                    put(
                        AgentCompletionHooks.NOTIFY_URL_ENV,
                        "${session.baseUrl}/send?token=${session.token}"
                    )
                    if (path.isNotEmpty()) put("PATH", path)
                    agentBinaries["codex"]?.let { put(AgentCompletionHooks.CODEX_BINARY_ENV, it) }
                    agentBinaries["claude"]?.let { put(AgentCompletionHooks.CLAUDE_BINARY_ENV, it) }
                    agentBinaries["opencode"]?.let { put(AgentCompletionHooks.OPENCODE_BINARY_ENV, it) }
                    if (openCodeConfig != null) {
                        put(AgentCompletionHooks.OPENCODE_INLINE_CONFIG_ENV, openCodeConfig)
                        put(AgentCompletionHooks.OPENCODE_RUNTIME_CONFIG_ENV, openCodeConfig)
                    }
                }

                return TuiCompletionRuntime(
                    environment = environment,
                    shellActivationCommand = if (!isWindows && wrappers.isNotEmpty()) {
                        AgentCompletionHooks.posixPathActivation(adapterDirectory.toString())
                    } else {
                        null
                    },
                    wrappers = wrappers,
                    bridgeSessionId = session.sessionId
                )
            } catch (failure: Throwable) {
                IdeBridge.removeSession(session.sessionId)
                throw failure
            }
        }

        @Synchronized
        private fun ensureAdapterFiles(directory: Path, isWindows: Boolean): AdapterFiles {
            Files.createDirectories(directory)
            val notifier = if (isWindows) {
                write(directory.resolve("notify-agentellij.ps1"), AgentCompletionHooks.windowsNotifier())
            } else {
                writeExecutable(directory.resolve("notify-agentellij"), AgentCompletionHooks.posixNotifier())
            }
            val codexWrapper = if (isWindows) {
                val powerShell = write(
                    directory.resolve("codex-agentellij.ps1"),
                    AgentCompletionHooks.codexWindowsWrapper(notifier.toString())
                )
                write(
                    directory.resolve("codex.cmd"),
                    AgentCompletionHooks.windowsCommandShim(powerShell.toString())
                )
            } else {
                writeExecutable(
                    directory.resolve("codex"),
                    AgentCompletionHooks.codexPosixWrapper(notifier.toString())
                )
            }
            val notifierCommand = if (isWindows) {
                AgentCompletionHooks.windowsNotifierCommand(notifier.toString(), "claude")
            } else {
                AgentCompletionHooks.posixNotifierCommand(notifier.toString(), "claude")
            }
            val settings = write(
                directory.resolve("claude-settings.json"),
                AgentCompletionHooks.claudeSettings(notifierCommand, mapper)
            )
            val claudeWrapper = if (isWindows) {
                val powerShell = write(
                    directory.resolve("claude-agentellij.ps1"),
                    AgentCompletionHooks.claudeWindowsWrapper(settings.toString())
                )
                write(
                    directory.resolve("claude.cmd"),
                    AgentCompletionHooks.windowsCommandShim(powerShell.toString())
                )
            } else {
                writeExecutable(
                    directory.resolve("claude"),
                    AgentCompletionHooks.claudePosixWrapper(settings.toString())
                )
            }
            val openCodePlugin = write(
                directory.resolve("opencode-agentellij.js"),
                AgentCompletionHooks.openCodePlugin()
            )
            val openCodeWrapper = if (isWindows) {
                val powerShell = write(
                    directory.resolve("opencode-agentellij.ps1"),
                    AgentCompletionHooks.openCodeWindowsWrapper()
                )
                write(
                    directory.resolve("opencode.cmd"),
                    AgentCompletionHooks.windowsCommandShim(powerShell.toString())
                )
            } else {
                writeExecutable(
                    directory.resolve("opencode"),
                    AgentCompletionHooks.openCodePosixWrapper()
                )
            }
            return AdapterFiles(notifier, codexWrapper, claudeWrapper, openCodeWrapper, openCodePlugin)
        }

        private fun write(path: Path, content: String): Path {
            if (Files.exists(path) && Files.readString(path, StandardCharsets.UTF_8) == content) return path

            val temporary = Files.createTempFile(path.parent, ".${path.fileName}-", ".tmp")
            try {
                Files.writeString(temporary, content, StandardCharsets.UTF_8)
                try {
                    Files.move(
                        temporary,
                        path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temporary)
            }
            return path
        }

        private fun writeExecutable(path: Path, content: String): Path = write(path, content).also {
            if (!it.toFile().setExecutable(true, true)) {
                throw IllegalStateException("Could not make completion adapter executable: $it")
            }
        }

        private data class AdapterFiles(
            val notifier: Path,
            val codexWrapper: Path,
            val claudeWrapper: Path,
            val openCodeWrapper: Path,
            val openCodePlugin: Path
        )
    }
}
