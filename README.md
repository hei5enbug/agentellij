# AgentellIJ

**AI coding agents, inside IntelliJ IDEA.**

[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/com.agentellij?label=Marketplace&logo=jetbrains&logoColor=white)](https://plugins.jetbrains.com/plugin/com.agentellij)
[![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ_IDEA-2025.1+-blue?logo=intellijidea&logoColor=white)](https://www.jetbrains.com/idea/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

AgentellIJ embeds AI coding agents directly into your IDE. No terminal switching, no copy-pasting file paths — chat with your agent in a native tool window while it reads, writes, and navigates your codebase in real time.

Designed to work with **[OpenCode](https://github.com/sst/opencode)**, **Claude Code**, **Codex**, and other terminal-based AI coding agents.

![AgentellIJ Screenshot](docs/media/media-screenshot-1.png)

## Features

- **Flexible Modes** — Switch between the interactive terminal and embedded web UI from the tool-window toolbar without restarting.
- **Multiple Agents** — Work with OpenCode, Claude Code, Codex CLI, or the native Terminal agent in one tool window.
- **Native Terminal Agent** — Open the IDE's persistent interactive shell with no installation or binary required.
- **Toolbar Agent Selector** — Change the active agent quickly, with each agent's binary path remembered separately.
- **Consent-Based CLI Installation** — See the exact install command before it runs, and start installation only after you choose **Install**.
- **Real-Time Context Sync** — Open files and the active editor stay available to the agent automatically; selected lines are shared with a shortcut or context-menu action.
- **Context Shortcuts** — Add files or selected lines to the agent's context from the editor or project tree.
- **Drag & Drop** — Add project files to the agent's context by dropping them into the chat.
- **Background Process** — Keep your workspace uncluttered while the agent runs in the background.
- **Per-Project Sessions** — Keep each project's agent connection isolated.
- **Custom Launch Arguments** — Tailor how each agent starts with additional CLI arguments.
- **Completion Notifications** — Receive an IntelliJ notification when OpenCode, Claude Code, or Codex finishes a response in the web UI, its direct TUI, or the native Terminal surface.

## Prerequisites

- **IntelliJ IDEA** 2025.1 or later (Community or Ultimate)
- **JetBrains Runtime with an embedded browser** — Required for the embedded browser (the default JetBrains Runtime includes it)
- **An AI coding agent** — Any terminal-based AI coding agent. For example:
  - [OpenCode](https://github.com/sst/opencode) — `npm i -g opencode-ai`
  - [Claude Code](https://docs.anthropic.com/en/docs/claude-code) — `npm i -g @anthropic-ai/claude-code`
  - [Codex CLI](https://github.com/openai/codex) — `npm i -g @openai/codex`

## Installation

### From JetBrains Marketplace

Install directly from your IDE: **Settings > Plugins > Marketplace** — search **"AgentellIJ"**.

### From Source

```bash
git clone https://github.com/hei5enbug/agentellij.git
cd agentellij
./gradlew buildPlugin
```

The plugin zip will be at `build/distributions/agentellij-*.zip`.

Install it in IntelliJ: **Settings > Plugins > ⚙️ > Install Plugin from Disk...**

> **Note:** Installing, updating, enabling, or disabling the plugin requires an IDE restart.

## Usage

### Opening the Chat

Click the **AgentellIJ** tool window on the right sidebar (or find it via **View > Tool Windows > AgentellIJ**). The plugin will automatically:

1. Launch the agent backend in a hidden terminal tab
2. Detect the server URL from stdout
3. Load the web UI in the embedded browser

AgentellIJ supports two tool-window modes. **TUI mode** is the default and runs the agent's interactive CLI directly inside the tool window through the terminal wrapper. **GUI mode** uses the embedded web UI.

The tool window toolbar has an **agent selector** and a **mode toggle**. Pick an agent from the selector dropdown and click **Change** to switch the active agent (Change stays disabled until you pick a different agent). Use the mode toggle to switch between GUI and TUI instantly — no restart required; terminal-only agents such as Claude Code and Codex CLI stay in TUI. The Terminal agent opens the IDE's persistent interactive shell at the project root with no installation or binary needed.

AgentellIJ notifies you when a supported agent finishes a response. In the native Terminal surface, normally invoking `codex`, `claude`, or `opencode` uses session-scoped adapters; no user-level agent configuration is changed, and the adapters have no authenticated callback after that AgentellIJ terminal closes. A completion notification means the agent finished its current turn—it can also be a question or a blocked/error report, not necessarily proof that every requested task succeeded.

### Keyboard Shortcuts

All context actions share a single smart shortcut — the plugin automatically selects the appropriate action (file, lines, or directory) based on focus and selection state.

| Action | Windows / Linux | macOS |
|---|---|---|
| Add to context | `Ctrl+Shift+I` | `Cmd+Shift+I` |

### Context Menu Actions

Right-click in the **editor** or **editor tab**:
- **AgentellIJ: Add File to Context** — Sends the full file path. The same shortcut (`Ctrl+Shift+I` / `Cmd+Shift+I`) triggers this when the editor or tab is focused without a text selection.
- **AgentellIJ: Add Lines to Context** — Sends the file path with line range (e.g., `src/Main.kt:10-25`). The same shortcut triggers this when text is selected in the editor.

Right-click in the **Project tree**:
- **AgentellIJ: Add to Context** — Sends selected file(s) or directory. The same shortcut triggers this when focus is in the project tree.

### Drag & Drop

Drag files from IntelliJ's project tree and drop them onto the chat window to add them as context.

## Configuration

**Settings > Tools > AgentellIJ**

Use the **Mode** dropdown to set the default mode on startup, or use the **toolbar toggle button** in the tool window to switch between GUI and TUI instantly at any time. AgentellIJ validates the selected mode against the active agent, so terminal-only agents such as Claude Code and Codex CLI stay in TUI mode. The rest of the settings apply in both modes, including custom binary path and additional CLI arguments.

If the selected agent CLI is missing, AgentellIJ shows an install prompt with the exact command it will run. Installation only starts after you click **Install**; you can also open settings, configure a custom binary path, or retry detection without installing anything.

Each agent stores its own binary path — switching the **Agent** dropdown preserves the path entered for each agent.

| Setting | Description | Default |
|---|---|---|
| Agent | Which AI coding agent to use | `OpenCode` |
| Mode | Tool window runtime: interactive terminal (**TUI**) or embedded web UI (**GUI**) | `TUI` |
| Agent binary path | Absolute path to the agent executable | _(empty — auto-detects from `PATH` or common install locations)_ |
| Additional arguments | Extra CLI args appended after the agent binary; quoted values and escaped spaces are preserved | _(empty)_ |

Bridge sessions are project-specific, but persistent agent state such as OpenCode `kv.json`, `model.json`, and `settings.json` follows the agent profile's state directory. Treat that state as user/agent scoped unless a future project-scoped state store is introduced.

### Environment Variables

| Variable | Description |
|---|---|
| `AGENTELLIJ_BIN` | Path to the agent binary (overrides `PATH` lookup) |
| `OPENCODE_BIN` | Legacy fallback for OpenCode users |
| `CLAUDE_CODE_BIN` | Path to the Claude Code binary |
| `CODEX_BIN` | Path to the Codex CLI binary |
| `CODEX_INSTALL_DIR` | Codex CLI install directory scanned during auto-detection |
| `CODEX_HOME` | Codex home directory used to scan managed standalone installs |

**Resolution order:** Settings path > `AGENTELLIJ_BIN` > agent-specific env vars (e.g., `OPENCODE_BIN`, `CLAUDE_CODE_BIN`, `CODEX_BIN`) > discovered binary (`PATH` first, then common install locations) > agent's default binary name

## Architecture

See [`docs/overview.md`](docs/overview.md) for what the plugin does and where it stops, and
[`docs/architecture.md`](docs/architecture.md) for how the code is divided.

## Development

See [`docs/development.md`](docs/development.md) for requirements, the verification commands, and the
testing rules.

## Contributing

Contributions are welcome! Please open an issue first to discuss what you'd like to change.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes (`git commit -m 'Add my feature'`)
4. Push to the branch (`git push origin feature/my-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
