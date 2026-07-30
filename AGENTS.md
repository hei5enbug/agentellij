# AGENTS.md

## What this project is

AgentellIJ hosts a terminal-based AI coding agent inside IntelliJ IDEA.

The agent command-line tool owns the conversation, the reasoning, the tool calls, and the stored
conversation state. The plugin owns the surface that displays it and the IntelliJ context it feeds in.
The plugin never becomes the owner of conversation or agent state, and any cache in the bundled web
client must have a size limit and an eviction policy.

Before writing anything, name which of these the change is:

- `host` — provides a surface
- `agent` — manages the agent tool as a resource
- `ide` — reads or writes IntelliJ state
- `bridge` — carries data across the plugin and agent boundary
- `config` — persists configuration
- `common` — a dependency-free helper for the above

If it is none of them, it belongs to the agent tool, not here.

## Always

1. Do not drift from what this project is.
2. Respect the layering rules. Code that can run without IntelliJ goes in `core`; everything else goes
   in `platform`.
3. Logic carries tests.
4. Do not claim a change is complete without running the verification commands.

## Where to look

| Doing this | Read |
|---|---|
| Adding a feature, or judging whether one belongs here | `docs/overview.md` |
| Adding or moving a package, or deciding where code goes | `docs/architecture.md` |
| Touching settings, `plugin.xml`, the bridge protocol, or agent state files | `docs/contracts.md` |
| Writing tests, or checking a change is finished | `docs/development.md` |
| Adding anything with a lifetime: listeners, widgets, browsers, timers, sessions | `docs/development.md` |

The Korean translation lives in `docs/ko/`. This English file is canonical.
