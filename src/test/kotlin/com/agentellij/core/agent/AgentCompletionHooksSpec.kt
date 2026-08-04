package com.agentellij.core.agent

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class AgentCompletionHooksSpec : BehaviorSpec({
    val mapper = jacksonObjectMapper()

    Given("the terminal child-process contract") {

        When("its environment names are read") {
            Then("they keep the released spellings used by the stable adapters") {
                AgentCompletionHooks.NOTIFY_URL_ENV shouldBe "AGENTELLIJ_NOTIFY_URL"
                AgentCompletionHooks.CODEX_BINARY_ENV shouldBe "AGENTELLIJ_CODEX_BIN"
                AgentCompletionHooks.CLAUDE_BINARY_ENV shouldBe "AGENTELLIJ_CLAUDE_BIN"
                AgentCompletionHooks.OPENCODE_BINARY_ENV shouldBe "AGENTELLIJ_OPENCODE_BIN"
                AgentCompletionHooks.OPENCODE_RUNTIME_CONFIG_ENV shouldBe
                    "AGENTELLIJ_OPENCODE_CONFIG_CONTENT"
                AgentCompletionHooks.OPENCODE_INLINE_CONFIG_ENV shouldBe "OPENCODE_CONFIG_CONTENT"
            }
        }
    }

    Given("the Codex adapter for a terminal session") {

        When("a POSIX wrapper is rendered around the real binary") {
            val script = AgentCompletionHooks.codexPosixWrapper(
                notifier = "/private/tmp/it's-agentellij/notify"
            )

            Then("it reads the real binary from the AgentellIJ terminal session") {
                script shouldContain AgentCompletionHooks.CODEX_BINARY_ENV
                script shouldContain "exec \"${'$'}real_binary\""
            }

            Then("it keeps the notifier path shell-safe") {
                script shouldContain "\"/private/tmp/it'\"'\"'s-agentellij/notify\""
            }

            Then("it configures only Codex's supported turn-complete notifier") {
                script shouldContain "notify=["
                script shouldContain "\"codex\""
                script shouldContain "\"${'$'}@\""
            }
        }

        When("a Windows wrapper is rendered") {
            val script = AgentCompletionHooks.codexWindowsWrapper(
                notifier = "C:\\Temp\\AgentellIJ\\notify.ps1"
            )

            Then("PowerShell forwards the original arguments after the notification override") {
                script shouldContain AgentCompletionHooks.CODEX_BINARY_ENV
                script shouldContain "& ${'$'}RealBinary @Prefix @args"
                script shouldContain "notify=["
                script shouldContain "'--config'"
            }
        }
    }

    Given("the Claude Code adapter for a terminal session") {

        When("the additional settings file is rendered") {
            val json = AgentCompletionHooks.claudeSettings(
                notifierCommand = "'/private/tmp/notify' 'claude'",
                mapper = mapper
            )
            val root = mapper.readTree(json)
            val hook = root["hooks"]["Stop"][0]["hooks"][0]

            Then("it registers a bounded command hook on the main agent Stop event") {
                hook["type"].asText() shouldBe "command"
                hook["command"].asText() shouldBe "'/private/tmp/notify' 'claude'"
                hook["timeout"].asInt() shouldBe 5
            }
        }
    }

    Given("the OpenCode adapter for a terminal or web session") {

        When("a POSIX TUI wrapper is rendered") {
            val script = AgentCompletionHooks.openCodePosixWrapper()

            Then("it restores the merged runtime config after shell startup and invokes the real binary") {
                script shouldContain AgentCompletionHooks.OPENCODE_BINARY_ENV
                script shouldContain AgentCompletionHooks.OPENCODE_RUNTIME_CONFIG_ENV
                script shouldContain "export OPENCODE_CONFIG_CONTENT=\"${'$'}runtime_config\""
                script shouldContain "exec \"${'$'}real_binary\" \"${'$'}@\""
            }
        }

        When("the runtime plugin is rendered") {
            val plugin = AgentCompletionHooks.openCodePlugin()

            Then("it listens for the documented idle event") {
                plugin shouldContain "session.idle"
            }

            Then("it posts the common bridge message only when AgentellIJ supplied a URL") {
                plugin shouldContain AgentCompletionHooks.NOTIFY_URL_ENV
                plugin shouldContain "agent.turnCompleted"
                plugin shouldContain "agentId: \"opencode\""
            }
        }

        When("the user already has inline configuration and plugins") {
            val merged = AgentCompletionHooks.withOpenCodePlugin(
                existing = """{"model":"provider/model","plugin":["existing-plugin"]}""",
                pluginUri = "file:///tmp/agentellij.js",
                mapper = mapper
            )!!
            val root = mapper.readTree(merged)

            Then("their configuration is preserved and the runtime plugin is appended") {
                root["model"].asText() shouldBe "provider/model"
                root["plugin"].map { it.asText() }.shouldContainExactly(
                    "existing-plugin",
                    "file:///tmp/agentellij.js"
                )
            }
        }

        When("the runtime plugin is already present") {
            val merged = AgentCompletionHooks.withOpenCodePlugin(
                existing = """{"plugin":["file:///tmp/agentellij.js"]}""",
                pluginUri = "file:///tmp/agentellij.js",
                mapper = mapper
            )!!

            Then("it is not added a second time") {
                mapper.readTree(merged)["plugin"].size() shouldBe 1
            }
        }

        When("the inherited inline configuration is malformed") {
            Then("it is refused rather than overwritten") {
                AgentCompletionHooks.withOpenCodePlugin(
                    existing = "{not-json",
                    pluginUri = "file:///tmp/agentellij.js",
                    mapper = mapper
                ).shouldBeNull()
            }
        }

        When("there is no inherited inline configuration") {
            val merged = AgentCompletionHooks.withOpenCodePlugin(
                existing = null,
                pluginUri = "file:///tmp/agentellij.js",
                mapper = mapper
            )!!

            Then("a minimal plugin-only configuration is created") {
                mapper.readTree(merged)["plugin"][0].asText() shouldBe "file:///tmp/agentellij.js"
            }
        }
    }

    Given("the notifier shared by command hooks") {

        When("the POSIX script is rendered") {
            val script = AgentCompletionHooks.posixNotifier()

            Then("it accepts only supported command-hook agents and cannot block them on a bridge failure") {
                script shouldContain "codex|claude"
                script shouldContain "--max-time 2"
                script shouldContain "|| true"
            }
        }

        When("the Windows script is rendered") {
            val script = AgentCompletionHooks.windowsNotifier()

            Then("it applies the same bounded best-effort behavior") {
                script shouldContain "-TimeoutSec 2"
                script shouldContain "catch"
                script shouldContain "agent.turnCompleted"
            }
        }
    }

    Given("a regular POSIX shell opened inside AgentellIJ") {

        When("its profile may have reordered PATH during startup") {
            Then("the activation command safely restores the session wrappers to the front") {
                AgentCompletionHooks.posixPathActivation("/Users/me/Agent Tools/it's-here") shouldBe
                    "export PATH='/Users/me/Agent Tools/it'\"'\"'s-here':\"${'$'}PATH\""
            }
        }
    }
})
