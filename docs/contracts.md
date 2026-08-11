# Contracts

Values that break an existing installation if they change.

The code is the source of truth for everything here. This document says which values are load-bearing
and which files must change together; it is not a copy to be edited on its own.

`ContractSpec` and `LayeringSpec` in `src/test/kotlin/com/agentellij/architecture` check most of this
automatically. Where a contract is only checked by hand, this document says so.

## Persisted settings

Settings are stored under a component name and a file name that have nothing to do with where the
class lives. Both were kept when the class moved packages.

| Value | Must stay |
|---|---|
| Component name | `com.agentellij.settings.AgentellIJSettings` |
| Storage file | `AgentellIJSettings.xml` |
| Field names | `mode`, `activeAgent`, `agentPath`, `claudeAgentPath`, `codexAgentPath`, `customArgs` |

Changing any of them silently discards every existing user's configuration: the IDE finds no matching
component and writes defaults.

The component name still contains `com.agentellij.settings`, a package that no longer exists. That is
deliberate, and the source scan in `LayeringSpec` ignores string literals so it is not reported as a
leftover.

Changing the settings shape means changing `platform/config/AgentellIJSettings.kt`,
`core/settings/AgentPaths.kt`, and `core/settings/AgentModePolicy.kt`.

## Plugin registration

`src/main/resources/META-INF/plugin.xml` names classes, identifiers and shortcuts as strings, so the
compiler cannot catch a mismatch.

| Value | Must stay |
|---|---|
| Settings page identifier | `com.agentellij.settings` |
| Action identifiers | `com.agentellij.AddDirectoryToContext`, `com.agentellij.AddToContext`, `com.agentellij.AddLinesToContext` |
| Menu groups | `ProjectViewPopupMenu`, `EditorPopupMenu`, `EditorTabPopupMenu` |
| Shortcut | `ctrl shift I` on the default keymap, `meta shift I` on both macOS keymaps, for all three actions |
| Bundle keys | The seven keys in `src/main/resources/messages/AgentellIJBundle.properties` |

The settings page identifier does not match the package the panel now lives in. Renaming it to match
would drop settings search history and any saved link to the page, and would gain nothing.

Moving a registered class means editing `plugin.xml` in the same commit. Splitting the two leaves a
commit where the plugin cannot load, so nothing can be verified at that point.

## Embedded browser dependency

The embedded browser classes under `com.intellij.ui.jcef` sat in the platform core until 2026.1. From
2026.2 they ship as a bundled plugin, so a plugin that names none of them in its dependencies cannot
load them at all.

| Value | Must stay |
|---|---|
| Dependency | `com.intellij.modules.jcef`, declared optional in `plugin.xml` with `config-file="com.agentellij-jcef.xml"` |
| Additional descriptor | `META-INF/com.agentellij-jcef.xml`, with no conditional registrations |
| Build dependency | `bundledPlugin("com.intellij.modules.jcef")`, which resolves only on 2026.2 and later |

The dependency is optional because 2025.1 and 2025.2 never declared that identifier. A required
dependency would stop the plugin from loading there, and an absent one leaves the graphical mode
without a browser on 2026.2. Either way the graphical mode keeps its own runtime check, so a runtime
without a working browser still gets the notice instead of a failure.

The additional descriptor stays empty because every extension must load on 2025.1 and 2025.2, where
the dependency identifier is unavailable even though the browser classes remain in the platform.

## Bridge protocol

The bundled web client under `src/main/resources/webui` is the other half of this contract. A change
here is a change to both sides.

| Element | Value |
|---|---|
| Address | `/idebridge/{sessionId}/{action}?token={token}` on a random loopback port |
| Actions | `events` for the event stream, `send` for a message |
| Message types | `openFile`, `openUrl`, `reloadPath`, `kv.get`, `kv.update`, `model.get`, `model.update`, `settings.get`, `settings.update`, `agent.turnCompleted`, `agent.inputRequested` |
| Event envelope | `type`, `payload`, `timestamp` |
| Reply envelope | `replyTo`, `ok`, then `error` or `payload`, and `timestamp` |
| No reply | A message without an identifier is answered with nothing at all |
| Origins | Loopback hosts only, over http or https |
| Assets | Served under `/ui`; a path that escapes the bundled directory is refused |

Changing the message types means changing `core/bridge/BridgeRoutes.kt`,
`platform/bridge/MessageHandler.kt`, and `src/main/resources/webui/js/core/ide-bridge.js`.
The two agent-notification types are also emitted by `core/agent/AgentCompletionHooks.kt`, which
renders the terminal adapters.

## Terminal notification adapters

AgentellIJ writes a fixed-size adapter set under the IDE system directory at
`agentellij/completion`. The files contain no token, project path, conversation state or resolved
agent binary. Per-terminal values arrive through child-process environment variables, and closing
the mode removes the bridge session that authenticates its callback.

| Agent | Main-turn completion source | Structured-question source |
|---|---|---|
| Codex CLI | `notify` configured by the session wrapper; the supported event is `agent-turn-complete` | A `PreToolUse` lifecycle hook matching `request_user_input` |
| Claude Code | An additional settings file containing a bounded `Stop` command hook | A `PreToolUse` hook matching `AskUserQuestion` |
| OpenCode | An inline runtime plugin listening for `session.idle` | The same plugin listening for `question.asked` |

Codex command hooks are subject to Codex's hook trust model. The stable AgentellIJ hook must be
reviewed once with `/hooks`; AgentellIJ does not bypass trust or modify the user's global Codex
configuration.

The native Terminal prepends all three supported-agent wrappers after its interactive shell starts.
A directly selected TUI is routed through its wrapper explicitly. OpenCode receives its plugin
through `OPENCODE_CONFIG_CONTENT`, merged with any inherited inline configuration rather than
replacing it. The generic Terminal profile itself is not an AI agent and never sends an
agent-notification message.

## Line range notation

A selection added to the agent's context is written as `path:start-end`, with one-based line numbers.
The bridge reads the same notation back when the agent asks to open the file.

`core/context/LineRangePath.kt` writes it and `core/bridge/OpenFileRequest.kt` reads it. A property
test round-trips one through the other, so a change to either side that the other does not follow
fails the build.

## Agent state files

The agent owns these files. The plugin proxies them for the web client and caches nothing.

| Element | Value |
|---|---|
| Files | `kv.json`, `model.json`, `settings.json` |
| Location | `$XDG_STATE_HOME/opencode`, or `~/.local/state/opencode` when the variable is unset |
| Model shape | Always the three keys `recent`, `favorite` and `variant` |
| Merge rules | `recent` and `favorite` are replaced whole; `variant` and key-value state merge per key |
| Theme | Kept only when it is the text `light` or `dark` |
| Writing | To a temporary file, then moved into place, falling back to a plain move where an atomic one is refused |
| Unreadable file | Copied aside as `.corrupt`, then `.corrupt.1` and so on, never overwritten |

Only OpenCode keeps state on disk. The other three agents return empty values.

## Agent records

Adding a field to the agent contract means changing `core/agent/AgentProfile.kt` and all four records
beside it: `OpenCodeProfile`, `ClaudeCodeProfile`, `CodexCliProfile`, `TerminalProfile`. The values
each record carries are pinned by `AgentProfileContractSpec`.

The catalogue order matters. When no agent matches, selection falls back to the first entry, so
reordering `core/agent/AgentCatalog.kt` changes which agent a new user gets.

## Environment variables

| Variable | Read for |
|---|---|
| `AGENTELLIJ_BIN` | An agent binary, after the configured path and before the agent's own variable |
| `OPENCODE_BIN`, `CLAUDE_CODE_BIN`, `CODEX_BIN` | That agent's binary |
| `OPENCODE_INSTALL_DIR`, `CODEX_INSTALL_DIR`, `CODEX_HOME`, `XDG_BIN_DIR` | Directories to search |
| `NVM_DIR`, `FNM_DIR`, `NPM_CONFIG_PREFIX`, `NPM_CONFIG_USERCONFIG`, `XDG_DATA_HOME` | Node tooling locations |
| `LOCALAPPDATA`, `APPDATA`, `USERPROFILE` | Windows install locations |
| `XDG_STATE_HOME` | Where OpenCode keeps conversation state |
| `SHELL`, `ComSpec`, `PATH` | Which shell runs the agent, and what it can find |

The following variables are written only into an AgentellIJ terminal child; they are not read as
host configuration.

| Variable | Passed to the child for |
|---|---|
| `AGENTELLIJ_NOTIFY_URL` | The loopback completion endpoint with its session token |
| `AGENTELLIJ_CODEX_BIN`, `AGENTELLIJ_CLAUDE_BIN`, `AGENTELLIJ_OPENCODE_BIN` | The real binary behind a stable session wrapper |
| `AGENTELLIJ_OPENCODE_CONFIG_CONTENT` | The merged OpenCode runtime config preserved across shell startup |
| `OPENCODE_CONFIG_CONTENT` | The inherited inline config plus the AgentellIJ runtime plugin |
| `PATH` | The stable supported-agent wrappers before the inherited search path |

## Known defects kept on purpose

Each of these is wrong, and each was left alone because fixing it changes behaviour users may already
depend on. They are recorded so the next reader does not mistake them for oversights.

| Defect | Where |
|---|---|
| The five second limit on path lookup does not bound the read that precedes it, so a lookup producing no output can block | `platform/env/PathLookup.kt` |
| `XDG_STATE_HOME` set to an empty string counts as a value, placing agent state at `/opencode` rather than under the home directory | `core/agent/AgentStateLocation.kt` |
| Malformed percent-encoding in a request throws instead of returning a defined response | `core/bridge/StaticAssets.kt`, `core/bridge/BridgeRequest.kt` |
| The bridge shutdown path has no caller in production code | `platform/bridge/IdeBridge.kt` |
| The bridge server and its route dispatcher reference each other | `platform/bridge` |
| Quiet helpers still catch every failure. They now report each one through diagnostics, so nothing<br>is silent, but the catch is wider than it should be | `core/util/QuietHelpers.kt` |
| An agent that exits without announcing an address leaves the surface on "Starting" until the five minute timeout | `platform/surface/GuiModeContent.kt` |
| A path entry is matched as a substring, so `/usr/local/bin-old` suppresses `/usr/local/bin` | `core/launch/ProcessEnvironment.kt` |
| An absent search path gains a trailing empty entry, which POSIX reads as the working directory | `core/launch/ProcessEnvironment.kt` |
| Resetting the settings panel while another agent's path is being edited parks that text against the wrong agent | `platform/config/AgentellIJConfigurable.kt` |
| A retry leaves the previous bridge session open until the mode is closed | `platform/surface/GuiModeContent.kt` |
| The web client trims its message cache on session switch but not on creation, so empty sessions accumulate | `src/main/resources/webui/js/core/state.js` |

The first two are pinned by specs, so a well-meant correction fails the build rather than reaching a
user unannounced.
