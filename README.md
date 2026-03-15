# AgentellIJ

**AI coding agents, inside IntelliJ IDEA.**

[![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ_IDEA-2024.3+-blue?logo=intellijidea&logoColor=white)](https://www.jetbrains.com/idea/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

AgentellIJ embeds AI coding agents directly into your IDE. No terminal switching, no copy-pasting file paths — chat with your agent in a native tool window while it reads, writes, and navigates your codebase in real time.

Designed to work with **[OpenCode](https://github.com/sst/opencode)**, **Claude Code**, **Codex**, and other terminal-based AI coding agents.

<!-- 
Screenshot or GIF here. Example:
![AgentellIJ Demo](docs/demo.gif)
-->

## Features

- **Embedded Chat UI** — Agent's web interface rendered inside IntelliJ via JCEF (Chromium)
- **Real-Time Sync** — Open files, active editor, and selections are automatically pushed to the agent
- **Context Shortcuts** — Add files or selected lines to the AI context from editor or project tree
- **Drag & Drop** — Drop files from the project tree directly into the chat
- **Paste Path** — Insert file paths into the prompt via right-click
- **Background Process** — Agent runs in a hidden terminal tab; no window clutter
- **Per-Project Sessions** — Each project gets an isolated, token-secured session
- **Configurable** — Custom binary path and CLI arguments via **Settings > Tools > AgentellIJ**

## Prerequisites

- **IntelliJ IDEA** 2024.3 or later (Community or Ultimate)
- **JBR with JCEF** — Required for the embedded browser (default JetBrains Runtime includes it)
- **An AI coding agent** — Any agent that exposes a web UI via a local server. For example:
  - [OpenCode](https://github.com/sst/opencode) — `npm i -g opencode-ai`
  - [Claude Code](https://docs.anthropic.com/en/docs/claude-code) — _Coming soon_
  - [Codex](https://github.com/openai/codex) — _Coming soon_

## Installation

### From Source

```bash
git clone https://github.com/hei5enbug/agentellij.git
cd agentellij
./gradlew buildPlugin
```

The plugin zip will be at `build/distributions/agentellij-*.zip`.

Install it in IntelliJ: **Settings > Plugins > ⚙️ > Install Plugin from Disk...**

### From JetBrains Marketplace

> _Coming soon._

## Usage

### Opening the Chat

Click the **AgentellIJ** tool window on the right sidebar (or find it via **View > Tool Windows > AgentellIJ**). The plugin will automatically:

1. Launch the agent backend in a hidden terminal tab
2. Detect the server URL from stdout
3. Load the web UI in the embedded browser

### Keyboard Shortcuts

| Action | Windows / Linux | macOS |
|---|---|---|
| Add file to context | `Ctrl+,` | `Cmd+\` |
| Add selected lines to context | `Ctrl+Shift+,` | `Cmd+Shift+\` |

### Context Menu Actions

Right-click in the **editor** or **editor tab**:
- **AgentellIJ: Add File to Context** — Sends the full file path
- **AgentellIJ: Add Lines to Context** — Sends the file path with line range (e.g., `src/Main.kt:10-25`)

Right-click in the **Project tree**:
- **AgentellIJ: Add to Context** — Sends selected file(s)
- **AgentellIJ: Paste Path** — Inserts the file path into the chat prompt

### Drag & Drop

Drag files from IntelliJ's project tree and drop them onto the chat window to add them as context.

## Configuration

**Settings > Tools > AgentellIJ**

| Setting | Description | Default |
|---|---|---|
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
│   ├── AddFileToContextAction
│   ├── AddLinesToContextAction
│   ├── AddFromProjectTreeAction
│   └── PastePathAction
├── backend/           # Agent process lifecycle
│   ├── BackendLauncher        # Launches agent in terminal
│   ├── BackendProcess         # Process abstraction interface
│   └── TerminalBackendProcess # Terminal-based implementation
├── bridge/            # IDE ↔ Agent communication (HTTP + SSE)
│   ├── IdeBridge              # HTTP server on localhost (random port)
│   ├── BridgeSession          # Per-project session with token auth
│   └── MessageHandler         # Routes: openFile, reloadPath, kv/model/settings
├── context/           # Context passing to agent
│   ├── ContextSender          # Sends file paths via bridge
│   └── DragDropHandler        # AWT drag-and-drop → context
├── settings/          # Plugin configuration (persistent state)
│   ├── AgentellIJSettings     # State: binary path, custom args
│   └── AgentellIJConfigurable # Settings UI panel
└── ui/                # Tool window and browser
    ├── ChatToolWindowFactory  # JCEF browser + backend orchestration
    └── OpenFilesTracker       # Syncs open/active files to agent
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

### Project Requirements

- JDK 21
- Gradle (wrapper included)

## Contributing

Contributions are welcome! Please open an issue first to discuss what you'd like to change.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes (`git commit -m 'Add my feature'`)
4. Push to the branch (`git push origin feature/my-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
