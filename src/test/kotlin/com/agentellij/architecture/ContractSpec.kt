package com.agentellij.architecture

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

/**
 * Pins the values that reach outside the plugin.
 *
 * Renaming any of these is silent at compile time and breaks an installation that
 * already exists: settings vanish, the plugin refuses to load, or a shortcut stops
 * working. The plugin verifier checks that registered classes exist, but not that the
 * identifiers, shortcuts and bundle keys are the ones users already have.
 */
class ContractSpec : BehaviorSpec({

    val settingsSource = File("src/main/kotlin/com/agentellij/platform/config/AgentellIJSettings.kt").readText()
    val descriptor = File("src/main/resources/META-INF/plugin.xml").readText()
    val bundle = File("src/main/resources/messages/AgentellIJBundle.properties").readText()

    Given("the persisted settings of an installation that already exists") {

        When("the storage declaration is read") {
            Then("the component name still carries its original package, despite the move") {
                settingsSource shouldContain "name = \"com.agentellij.settings.AgentellIJSettings\""
            }

            Then("the file it is stored in is unchanged") {
                settingsSource shouldContain "Storage(\"AgentellIJSettings.xml\")"
            }
        }

        When("the stored fields are read") {
            Then("all six keep their names, because each one is a key in that file") {
                listOf("mode", "activeAgent", "agentPath", "claudeAgentPath", "codexAgentPath", "customArgs")
                    .forEach { field -> settingsSource shouldContain "var $field" }
            }
        }
    }

    Given("the plugin descriptor") {

        When("the registered classes are read") {
            Then("each one exists") {
                Regex("""(?:factoryClass|instance|serviceImplementation|implementation|class)="([\w.]+)"""")
                    .findAll(descriptor)
                    .map { it.groupValues[1] }
                    .forEach { className ->
                        val path = "src/main/kotlin/${className.replace('.', '/')}.kt"
                        File(path).exists() shouldBe true
                    }
            }
        }

        When("the settings page registration is read") {
            Then("its identifier is the released one, not the package it now lives in") {
                descriptor shouldContain "id=\"com.agentellij.settings\""
            }

            Then("it still appears under the tools group and uses the bundle key") {
                descriptor shouldContain "parentId=\"tools\""
                descriptor shouldContain "key=\"settings.displayName\""
            }
        }

        When("the actions are read") {
            Then("the three identifiers are unchanged") {
                Regex("""<action id="([\w.]+)"""").findAll(descriptor).map { it.groupValues[1] }.toList()
                    .shouldContainExactly(
                        "com.agentellij.AddDirectoryToContext",
                        "com.agentellij.AddToContext",
                        "com.agentellij.AddLinesToContext"
                    )
            }

            Then("each menu they attach to is unchanged") {
                descriptor shouldContain "group-id=\"ProjectViewPopupMenu\""
                descriptor shouldContain "group-id=\"EditorPopupMenu\""
                descriptor shouldContain "group-id=\"EditorTabPopupMenu\""
            }

            Then("the shared shortcut is still bound on all three keymaps for every action") {
                Regex("keymap=\"\\\$default\" first-keystroke=\"ctrl shift I\"").findAll(descriptor).count() shouldBe 3
                Regex("""keymap="Mac OS X" first-keystroke="meta shift I"""").findAll(descriptor).count() shouldBe 3
                Regex("""keymap="Mac OS X 10\.5\+" first-keystroke="meta shift I"""")
                    .findAll(descriptor).count() shouldBe 3
            }
        }
    }

    Given("the message bundle the descriptor points at") {

        When("its keys are read") {
            Then("every key the descriptor references is present") {
                Regex("""key="([\w.]+)"""").findAll(descriptor)
                    .map { it.groupValues[1] }
                    .forEach { key -> bundle shouldContain "$key=" }
            }

            Then("the action text and description keys are all present") {
                listOf("AddDirectoryToContext", "AddToContext", "AddLinesToContext").forEach { action ->
                    bundle shouldContain "action.com.agentellij.$action.text="
                    bundle shouldContain "action.com.agentellij.$action.description="
                }
            }
        }
    }
})
