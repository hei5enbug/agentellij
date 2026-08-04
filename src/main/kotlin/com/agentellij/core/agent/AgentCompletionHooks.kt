package com.agentellij.core.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * Renders the small, session-scoped adapters that turn each supported agent's native
 * completion event into the common AgentellIJ bridge message.
 *
 * The rendered files contain no IntelliJ code and are deterministic for their inputs,
 * so their quoting and configuration merging can be pinned without starting an IDE.
 */
internal object AgentCompletionHooks {
    const val NOTIFY_URL_ENV = "AGENTELLIJ_NOTIFY_URL"
    const val OPENCODE_INLINE_CONFIG_ENV = "OPENCODE_CONFIG_CONTENT"
    const val CODEX_BINARY_ENV = "AGENTELLIJ_CODEX_BIN"
    const val CLAUDE_BINARY_ENV = "AGENTELLIJ_CLAUDE_BIN"
    const val OPENCODE_BINARY_ENV = "AGENTELLIJ_OPENCODE_BIN"
    const val OPENCODE_RUNTIME_CONFIG_ENV = "AGENTELLIJ_OPENCODE_CONFIG_CONTENT"

    private const val COMPLETION_MESSAGE_TYPE = "agent.turnCompleted"

    fun posixNotifier(): String = """#!/bin/sh
agent_id=${'$'}1
case "${'$'}agent_id" in
  codex|claude) ;;
  *) exit 0 ;;
esac
notify_url=${'$'}{$NOTIFY_URL_ENV:-}
[ -n "${'$'}notify_url" ] || exit 0
body='{"type":"$COMPLETION_MESSAGE_TYPE","payload":{"agentId":"'"${'$'}agent_id"'"}}'
curl --fail --silent --show-error --max-time 2 \
  --request POST \
  --header 'Content-Type: application/json' \
  --data "${'$'}body" \
  "${'$'}notify_url" >/dev/null 2>&1 || true
"""

    fun windowsNotifier(): String = """param([string]${'$'}AgentId)
if (${ '$' }AgentId -notin @('codex', 'claude')) { exit 0 }
${'$'}NotifyUrl = ${'$'}env:$NOTIFY_URL_ENV
if ([string]::IsNullOrWhiteSpace(${'$'}NotifyUrl)) { exit 0 }
${'$'}Body = @{
  type = '$COMPLETION_MESSAGE_TYPE'
  payload = @{ agentId = ${'$'}AgentId }
} | ConvertTo-Json -Compress
try {
  Invoke-WebRequest -Uri ${'$'}NotifyUrl -Method Post -ContentType 'application/json' -Body ${'$'}Body -TimeoutSec 2 | Out-Null
} catch {
  # Completion notifications must never interfere with the agent.
}
"""

    fun codexPosixWrapper(notifier: String): String {
        val notifyConfig = "notify=[${tomlString(notifier)},${tomlString("codex")}]"
        return """#!/bin/sh
real_binary=${'$'}{$CODEX_BINARY_ENV:-}
[ -n "${'$'}real_binary" ] || exit 127
exec "${'$'}real_binary" --config ${posixQuote(notifyConfig)} "${'$'}@"
"""
    }

    fun claudePosixWrapper(settingsFile: String): String = """#!/bin/sh
real_binary=${'$'}{$CLAUDE_BINARY_ENV:-}
[ -n "${'$'}real_binary" ] || exit 127
exec "${'$'}real_binary" --settings ${posixQuote(settingsFile)} "${'$'}@"
"""

    fun openCodePosixWrapper(): String = """#!/bin/sh
real_binary=${'$'}{$OPENCODE_BINARY_ENV:-}
runtime_config=${'$'}{$OPENCODE_RUNTIME_CONFIG_ENV:-}
[ -n "${'$'}real_binary" ] || exit 127
[ -n "${'$'}runtime_config" ] || exit 127
export $OPENCODE_INLINE_CONFIG_ENV="${'$'}runtime_config"
exec "${'$'}real_binary" "${'$'}@"
"""

    fun codexWindowsWrapper(notifier: String): String {
        val notifyConfig = "notify=[" + listOf(
            "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", notifier, "codex"
        ).joinToString(",") { tomlString(it) } + "]"
        return windowsPowerShellWrapper(CODEX_BINARY_ENV, listOf("--config", notifyConfig))
    }

    fun claudeWindowsWrapper(settingsFile: String): String =
        windowsPowerShellWrapper(CLAUDE_BINARY_ENV, listOf("--settings", settingsFile))

    fun openCodeWindowsWrapper(): String = """${'$'}RealBinary = [Environment]::GetEnvironmentVariable('$OPENCODE_BINARY_ENV')
${'$'}RuntimeConfig = [Environment]::GetEnvironmentVariable('$OPENCODE_RUNTIME_CONFIG_ENV')
if ([string]::IsNullOrWhiteSpace(${'$'}RealBinary)) { exit 127 }
if ([string]::IsNullOrWhiteSpace(${'$'}RuntimeConfig)) { exit 127 }
${'$'}env:$OPENCODE_INLINE_CONFIG_ENV = ${'$'}RuntimeConfig
& ${'$'}RealBinary @args
exit ${'$'}LASTEXITCODE
"""

    fun windowsCommandShim(powerShellWrapper: String): String = """@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ${windowsCommandQuote(powerShellWrapper)} %*
"""

    fun posixPathActivation(wrapperDirectory: String): String =
        "export PATH=${posixQuote(wrapperDirectory)}:\"${'$'}PATH\""

    fun claudeSettings(notifierCommand: String, mapper: ObjectMapper): String {
        val root = mapper.createObjectNode()
        val hooks = root.putObject("hooks")
        val stop = hooks.putArray("Stop")
        val matcher = stop.addObject()
        matcher.putArray("hooks").addObject().apply {
            put("type", "command")
            put("command", notifierCommand)
            put("timeout", 5)
        }
        return mapper.writeValueAsString(root)
    }

    fun posixNotifierCommand(notifier: String, agentId: String): String =
        "${posixQuote(notifier)} ${posixQuote(agentId)}"

    fun windowsNotifierCommand(notifier: String, agentId: String): String =
        listOf(
            "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", notifier, agentId
        ).joinToString(" ") { windowsCommandQuote(it) }

    fun openCodePlugin(): String = """export const AgentellIJCompletionPlugin = async () => ({
  event: async ({ event }) => {
    if (event?.type !== "session.idle") return
    const notifyUrl = process.env.$NOTIFY_URL_ENV
    if (!notifyUrl) return
    try {
      await fetch(notifyUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          type: "$COMPLETION_MESSAGE_TYPE",
          payload: { agentId: "opencode" },
        }),
      })
    } catch (_) {
      // A notification failure must never affect the agent session.
    }
  },
})
"""

    /**
     * Appends the runtime plugin to OpenCode's inline configuration. OpenCode merges
     * this source with the user's normal configuration, so their providers, models and
     * existing plugins remain in force.
     *
     * Returns null rather than replacing malformed or non-object user content.
     */
    fun withOpenCodePlugin(existing: String?, pluginUri: String, mapper: ObjectMapper): String? {
        val root = when {
            existing.isNullOrBlank() -> mapper.createObjectNode()
            else -> try {
                mapper.readTree(existing) as? ObjectNode ?: return null
            } catch (_: Exception) {
                return null
            }
        }

        val plugins = when (val current = root.get("plugin")) {
            null -> root.putArray("plugin")
            is ArrayNode -> current
            else -> return null
        }
        if (plugins.none { it.isTextual && it.asText() == pluginUri }) plugins.add(pluginUri)
        return mapper.writeValueAsString(root)
    }

    private fun windowsPowerShellWrapper(binaryEnvironmentVariable: String, prefixArguments: List<String>): String {
        val arguments = prefixArguments.joinToString(", ") { "'${powerShellLiteral(it)}'" }
        return """${'$'}RealBinary = [Environment]::GetEnvironmentVariable('$binaryEnvironmentVariable')
if ([string]::IsNullOrWhiteSpace(${'$'}RealBinary)) { exit 127 }
${'$'}Prefix = @($arguments)
& ${'$'}RealBinary @Prefix @args
exit ${'$'}LASTEXITCODE
"""
    }

    private fun posixQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    private fun powerShellLiteral(value: String): String = value.replace("'", "''")

    private fun windowsCommandQuote(value: String): String =
        if (value.any { it.isWhitespace() || it == '"' }) "\"${value.replace("\"", "\"\"")}\"" else value

    private fun tomlString(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            append(
                when (ch) {
                    '\\' -> "\\\\"
                    '"' -> "\\\""
                    '\n' -> "\\n"
                    '\r' -> "\\r"
                    '\t' -> "\\t"
                    else -> ch
                }
            )
        }
        append('"')
    }
}
