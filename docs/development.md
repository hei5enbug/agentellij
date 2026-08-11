# Development

How to build, test and verify a change.

## Requirements

| Tool | Version |
|---|---|
| JDK | 21 |
| Gradle | 9.6.1, through the bundled wrapper |
| IntelliJ IDEA | 2025.1 through 2026.2, with a JetBrains Runtime that includes the embedded browser |

## Verification

| Command | Proves |
|---|---|
| `./gradlew test` | Behaviour, the layering rules, and the external contracts |
| `./gradlew build` | The plugin compiles and packages |
| `./gradlew koverVerify` | The pure layer stays covered |
| `./gradlew verifyPlugin --rerun-tasks` | The plugin loads on every supported IDE build |
| `./gradlew runIde` | A sandbox IDE with the plugin installed, for what tests cannot reach |

Run `test` and `build` on every change. `verifyPlugin` takes several minutes because it downloads and
checks against five IDE builds, so run it before finishing a piece of work and on any change to
`plugin.xml`, where a wrong class name is otherwise invisible until the plugin fails to load.

Continuous integration runs `test`, `build` and `verifyPlugin` on pull requests and pushes to `main`.

## Testing rules

### Tools

Kotest with `BehaviorSpec`, so given, when and then are enforced by the syntax rather than by
convention. Instancio fills the fields a spec does not care about. Kover measures the pure layer.

Instancio is used through `instancio-core`, not `instancio-junit`: Kotest runs its own engine on the
JUnit platform, so a JUnit Jupiter extension would never fire.

JUnit 4 is on the test runtime path because the IntelliJ platform jars reference it and the Gradle
test worker will not start without it. The vintage engine is deliberately absent, so no JUnit 4 test
can be discovered.

### F.I.R.S.T. in this repository

| Property | What it means here |
|---|---|
| Fast | No test sleeps. Waiting on time, a process or a scheduler means injecting a fake one |
| Independent | Nothing touches the real environment, the user's home directory, or a shared file. Temporary directories are created per scenario |
| Repeatable | Behaviour that differs by operating system is exercised by passing a flag, never by reading the host |
| Self-validating | No test needs a human to read output and judge |
| Timely | Behaviour is pinned before it is moved, so the move is what the test guards |

### Random data

Instancio fills only the fields a scenario does not care about. Any value being asserted on is set
explicitly. Following that rule keeps random filling from making a spec unrepeatable, and it makes the
fields a scenario ignores visible in its code.

Agent records are never generated. They are fixed values whose exact contents are the point.

### Writing values out

A test may write an expected value out and compare against it when that value is an external
contract: a storage key, a protocol name, a registered identifier, a default. Those are the values a
developer can change silently and a user will notice.

A test may not read a constant from the implementation and compare it to the same constant. That
passes whatever the value is.

### Tests not worth having

Getters, data class equality, framework behaviour, and mock verification that only restates the
implementation. If a failure cannot be described as a problem the user would have, the test is not
earning its place.

### Property tests

Two, both guarding an agreement between two modules that would otherwise drift: path relativisation
against reconstruction, and the line range notation against the parser that reads it back.

### What is not tested

The list, and the reasoning, are in `docs/architecture.md` under "What is not tested, and why".

## Resource lifetime

Anything that outlives a single call needs an owner and a disposal path. Listeners, terminal widgets,
browsers, timers, executors, bridge sessions and background tasks all qualify.

| Rule | Reason |
|---|---|
| Register disposal where the resource is created | Separating them is how a resource ends up with no owner |
| Parent to the mode disposable, not the tool window | A surface must not outlive the screen it drew |
| Whoever wraps a resource tears it down | The composition root owns only the disposable that triggers it |
| Do not hold a project reference past its session | It keeps a closed project alive |

Terminal completion adapters are a bounded set of files under the IDE system directory. A terminal
mode owns only its authenticated bridge session: its environment carries the callback and real
binary paths, and disposal removes the session so the stable adapter files become inert. The files
must not contain a project, token or conversation value.

## Manual checks

Some behaviour needs a running IDE. Run `./gradlew runIde` and check the following before releasing a
change that touches the tool window, the surfaces, or settings.

- The tool window opens on the right and starts the agent in terminal mode
- The agent selector lists all four agents, and Change is disabled until a different one is chosen
- Switching agents and switching modes both work repeatedly, with no errors in the log
- Settings saved by an earlier version still load, and per-agent paths stay separate
- Adding a file, a selection, and a tree selection to the context all reach the agent
- The install prompt appears for a missing agent and runs nothing before Install is clicked
- On the web surface, the interface loads and a file link opens at the right line
- Completing one main-agent response or opening its structured question UI in OpenCode web mode,
  each direct supported TUI, and each supported agent launched by name from the native Terminal
  produces both an IntelliJ balloon and an OS-native popup, including while IntelliJ is active
- Trust the stable Codex `request_user_input` hook through `/hooks`, then confirm that Codex questions
  notify without bypassing hook trust
- Closing or switching away from a terminal mode makes its former completion callback return
  unauthorized, and agents launched outside AgentellIJ produce no AgentellIJ notification
- Closing the IDE produces no errors

## Documentation

`docs/` holds four documents with non-overlapping scopes: this one, `docs/overview.md`,
`docs/architecture.md`, and `docs/contracts.md`. `AGENTS.md` at the repository root routes an AI agent
to the right one.

English is canonical. `docs/ko/` holds a Korean translation of each, with matching section structure.
A change to an English document is not finished until its Korean counterpart matches.

`README.md` is for users installing the plugin. `docs/` is for people changing it.

Before writing or editing changelogs, read every file in `changelogs/`; use present-tense action
sentences for concrete, user-relevant changes, one coherent change per `<li>`, with no trailing
period.

Unfinished work belongs in an issue, not in a document. The previous architecture document carried a
list of known deviations that grew to twenty-five entries, most of them never closed; a list like that
records intent without creating any pressure to act on it.
