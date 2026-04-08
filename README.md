# AgentellIJ

**AI coding agents, inside IntelliJ IDEA.**

[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/com.agentellij?label=Marketplace&logo=jetbrains&logoColor=white)](https://plugins.jetbrains.com/plugin/com.agentellij)
[![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ_IDEA-2025.1+-blue?logo=intellijidea&logoColor=white)](https://www.jetbrains.com/idea/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

AgentellIJ embeds AI coding agents directly into your IDE. No terminal switching, no copy-pasting file paths — chat with your agent in a native tool window while it reads, writes, and navigates your codebase in real time.

Designed to work with **[OpenCode](https://github.com/sst/opencode)**, **Claude Code**, **Codex**, and other terminal-based AI coding agents.

![AgentellIJ Screenshot](docs/media/media-screenshot-2.png)

## Features

- **Embedded Chat UI** — Agent's web interface rendered inside IntelliJ via JCEF (Chromium)
- **Real-Time Sync** — Open files, active editor, and selections are automatically pushed to the agent
- **Context Shortcuts** — Add files or selected lines to the AI context from editor or project tree (`Ctrl+Shift+I` / `Cmd+Shift+I`)
- **Drag & Drop** — Drop files from the project tree directly into the chat
- **Background Process** — Agent runs in a hidden terminal tab; no window clutter
- **Per-Project Sessions** — Each project gets an isolated, token-secured session
- **Configurable** — Custom binary path and CLI arguments via **Settings > Tools > AgentellIJ**

## Prerequisites

- **IntelliJ IDEA** 2025.1 or later (Community or Ultimate)
- **JBR with JCEF** — Required for the embedded browser (default JetBrains Runtime includes it)
- **An AI coding agent** — Any agent that exposes a web UI via a local server. For example:
  - [OpenCode](https://github.com/sst/opencode) — `npm i -g opencode-ai`

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

## Usage

### Opening the Chat

Click the **AgentellIJ** tool window on the right sidebar (or find it via **View > Tool Windows > AgentellIJ**). The plugin will automatically:

1. Launch the agent backend in a hidden terminal tab
2. Detect the server URL from stdout
3. Load the web UI in the embedded browser

AgentellIJ now supports two tool-window modes. **TUI mode** is the default and runs the agent's interactive CLI directly inside the tool window through the terminal wrapper. **GUI mode** keeps the existing JCEF-powered embedded web UI flow, which is useful when you prefer the browser-based experience.

### Keyboard Shortcuts

All context actions share a single smart shortcut — the plugin automatically selects the appropriate action (file, lines, or directory) based on focus and selection state.

| Action | Windows / Linux | macOS |
|---|---|---|
| Add to context | `Ctrl+Shift+I` | `Cmd+Shift+I` |

### Context Menu Actions

Right-click in the **editor** or **editor tab**:
- **AgentellIJ: Add File to Context** — Sends the full file path
- **AgentellIJ: Add Lines to Context** — Sends the file path with line range (e.g., `src/Main.kt:10-25`)

Right-click in the **Project tree**:
- **AgentellIJ: Add to Context** — Sends selected file(s) or directory

### Drag & Drop

Drag files from IntelliJ's project tree and drop them onto the chat window to add them as context.

## Configuration

**Settings > Tools > AgentellIJ**

Use the **Mode** dropdown to choose how the tool window runs. Select **GUI** to keep the embedded browser experience, or **TUI** to open the agent in interactive terminal mode inside the tool window. The rest of the settings still apply in both modes, including custom binary path and additional CLI arguments.

| Setting | Description | Default |
|---|---|---|
| Mode | Tool window runtime: interactive terminal (**TUI**) or embedded web UI (**GUI**) | `TUI` |
| Agent binary path | Absolute path to the agent executable | _(empty — uses `opencode` from `PATH`)_ |
| Additional arguments | Extra CLI args appended after the agent binary | _(empty)_ |

### Environment Variables

| Variable | Description |
|---|---|
| `AGENTELLIJ_BIN` | Path to the agent binary (overrides `PATH` lookup) |
| `OPENCODE_BIN` | Legacy fallback for OpenCode users |

**Resolution order:** Settings > `AGENTELLIJ_BIN` > `OPENCODE_BIN` > `opencode` from `PATH`

## Architecture

```
com.agentellij
├── actions/           # IDE actions (context menu, shortcuts)
│   ├── AddFileToContextAction     # Editor/tab → add file to context
│   ├── AddLinesToContextAction    # Editor → add selected lines to context
│   ├── AddDirectoryToContextAction # Project tree → add file(s)/directory
│   └── AgentellIJActionPromoter   # Prioritizes AgentellIJ actions in menus
├── backend/           # Agent process lifecycle & profile management
│   ├── AgentProfile               # Interface: agent-specific behavior
│   ├── AgentProfileResolver       # Resolves profile from settings/env
│   ├── OpenCodeProfile            # OpenCode agent implementation
│   ├── BackendLauncher            # Launches agent process with fallback
│   ├── BackendProcess             # Process abstraction interface
│   ├── DirectBackendProcess       # Direct process with piped stdout
│   └── TerminalBackendProcess     # Terminal widget wrapper
├── bridge/            # IDE ↔ Agent communication (HTTP + SSE)
│   ├── IdeBridge                  # HTTP server on localhost (random port)
│   ├── BridgeSession              # Per-project session with token auth
│   └── MessageHandler             # Routes: openFile, openUrl, reloadPath, kv, model, settings
├── context/           # Context passing to agent
│   ├── ContextSender              # Sends file paths via bridge
│   └── DragDropHandler            # AWT drag-and-drop → context
├── settings/          # Plugin configuration (persistent state)
│   ├── AgentellIJSettings         # State: binary path, custom args
│   └── AgentellIJConfigurable     # Settings UI panel
├── ui/                # Tool window and browser
│   ├── ChatToolWindowFactory      # Mode dispatcher (GUI vs TUI)
│   ├── GuiModeContent             # JCEF browser + backend orchestration
│   ├── TuiModeContent             # Terminal widget wrapper for interactive CLI
│   └── OpenFilesTracker           # Syncs open/active files to agent
└── util/              # Shared utilities
    └── SafeUtils                  # closeQuietly, runQuietly, resolveAbsolutePath
```

### Communication Flow

```
IntelliJ IDEA                          Agent Backend
┌─────────────┐                    ┌──────────────┐
│  Tool Window │◄── JCEF browser ──│   Web UI     │
│  (right bar) │                   │  (/app)      │
└──────┬───────┘                   └──────┬───────┘
       │                                  │
       ▼                                  ▼
┌─────────────┐    HTTP + SSE     ┌──────────────┐
│  IdeBridge   │◄────────────────►│  JS client   │
│  (localhost) │  token-secured   │  (in JCEF)   │
└──────┬───────┘                  └──────────────┘
       │
       ▼
┌─────────────────────────────────────────────────┐
│  MessageHandler                                  │
│  openFile · openUrl · reloadPath                 │
│  kv.get/update · model.get/update                │
│  settings.get/update                             │
└──────────────────────────────────────────────────┘
```

## Development

### Build

```bash
./gradlew build
```

### Run in IDE Sandbox

```bash
./gradlew runIde
```

This launches a sandboxed IntelliJ instance with the plugin pre-installed.

### Run Tests

```bash
./gradlew test
```

### Verify Plugin Compatibility

```bash
./gradlew verifyPlugin
```

Runs JetBrains Plugin Verifier against recommended IDE versions.

### Project Requirements

- JDK 21
- Gradle 9.4 (wrapper included)

## Contributing

Contributions are welcome! Please open an issue first to discuss what you'd like to change.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes (`git commit -m 'Add my feature'`)
4. Push to the branch (`git push origin feature/my-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
