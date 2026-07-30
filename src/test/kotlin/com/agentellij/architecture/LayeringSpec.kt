package com.agentellij.architecture

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import java.io.File

/**
 * Enforces the layering rules against the source tree itself.
 *
 * The previous structure carried its rules in a document, and fifteen of its
 * twenty-five recorded violations were still open when it was replaced. A rule that
 * only a reviewer checks is a rule that eventually stops holding, so these are checked
 * by the build instead.
 */
class LayeringSpec : BehaviorSpec({

    val sources = SourceTree.main()
    val coreSources = sources.filter { it.layer == "core" }

    Given("the pure layer, which has to run without IntelliJ") {

        When("its imports are examined") {
            Then("nothing imports the IntelliJ platform") {
                coreSources.violating { it.startsWith("com.intellij.") }.shouldBeEmpty()
            }

            Then("nothing imports the terminal or browser integrations") {
                coreSources.violating {
                    it.startsWith("org.jetbrains.plugins.") ||
                        it.startsWith("com.jediterm.") ||
                        it.startsWith("org.cef.")
                }.shouldBeEmpty()
            }

            Then("nothing imports a user interface toolkit") {
                coreSources.violating { it.startsWith("javax.swing.") || it.startsWith("java.awt.") }
                    .shouldBeEmpty()
            }

            Then("nothing imports the HTTP server") {
                coreSources.violating { it.startsWith("com.sun.net.") }.shouldBeEmpty()
            }

            Then("nothing imports the platform layer") {
                coreSources.violating { it.startsWith("com.agentellij.platform.") }.shouldBeEmpty()
            }
        }

        When("its code is examined") {
            Then("nothing reads the environment or system properties for itself") {
                coreSources.calling("System.getenv", "System.getProperty").shouldBeEmpty()
            }

            Then("nothing reads the clock for itself") {
                coreSources.calling("System.currentTimeMillis", "System.nanoTime").shouldBeEmpty()
            }

            Then("nothing starts a process for itself") {
                coreSources.calling("ProcessBuilder(").shouldBeEmpty()
            }

            Then("nothing reads the host's path separator") {
                coreSources.calling("File.pathSeparator").shouldBeEmpty()
            }

            Then("nothing touches the thread it happens to run on") {
                coreSources.calling("Thread.currentThread(").shouldBeEmpty()
            }
        }
    }

    Given("the dependency directions inside the pure layer") {

        When("imports between its packages are examined") {
            Then("every import follows an allowed direction") {
                val violations = coreSources.flatMap { source ->
                    source.imports
                        .filter { it.startsWith("com.agentellij.core.") }
                        .map { it.removePrefix("com.agentellij.core.").substringBeforeLast(".") }
                        .filter { it != source.packageName && it !in CoreDependencies.allowedFrom(source.packageName) }
                        .map { "${source.layer}.${source.packageName}/${source.name} -> core.$it" }
                }
                violations.shouldBeEmpty()
            }
        }
    }

    Given("the settings service, which reaches global state") {

        When("its users are examined") {
            Then("only the composition root and the settings panel reference it") {
                sources
                    .filter { "com.agentellij.platform.config.AgentellIJSettings" in it.imports }
                    .map { "${it.packageName}/${it.name}" }
                    .filterNot { it.startsWith("config/") || it == "toolwindow/AgentellIJWiring.kt" }
                    .shouldBeEmpty()
            }
        }
    }

    Given("the packages that were replaced by this structure") {

        When("package declarations and imports are examined") {
            Then("no reference to an old package survives") {
                val retired = listOf(
                    "com.agentellij.actions", "com.agentellij.agent", "com.agentellij.backend",
                    "com.agentellij.bridge", "com.agentellij.common", "com.agentellij.config",
                    "com.agentellij.context", "com.agentellij.settings", "com.agentellij.ui",
                    "com.agentellij.util"
                )
                sources.flatMap { source ->
                    (source.imports + "com.agentellij.${source.layer}.${source.packageName}")
                        .filter { reference -> retired.any { reference.startsWith("$it.") } }
                        .map { "${source.name}: $it" }
                }.shouldBeEmpty()
            }
        }
    }

    Given("declaration names across the whole tree") {

        When("top level names are collected") {
            Then("no name is declared in two packages, so no alias import is ever needed") {
                sources
                    .groupBy { it.name }
                    .filterValues { it.size > 1 }
                    .map { (name, files) -> "$name in ${files.map { it.packageName }}" }
                    .shouldBeEmpty()
            }
        }
    }
})

/** Allowed import directions between pure packages. */
private object CoreDependencies {
    private val ALLOWED = mapOf(
        "util" to emptySet<String>(),
        "text" to emptySet(),
        "agent" to setOf("text", "util"),
        "discovery" to setOf("util"),
        "launch" to setOf("agent", "discovery", "text", "util"),
        "settings" to setOf("agent", "util"),
        "context" to setOf("util"),
        "state" to setOf("text", "util"),
        "bridge" to setOf("context", "util"),
        "install" to setOf("text", "util")
    )

    fun allowedFrom(packageName: String): Set<String> = ALLOWED[packageName].orEmpty()
}

internal data class SourceFile(
    val name: String,
    val layer: String,
    val packageName: String,
    val imports: List<String>,
    val body: String
)

internal object SourceTree {
    private val IMPORT = Regex("""^import ([\w.]+)""", RegexOption.MULTILINE)

    fun main(): List<SourceFile> {
        val root = File("src/main/kotlin/com/agentellij")
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { file ->
                val text = file.readText()
                val relative = file.relativeTo(root).path.split(File.separatorChar)
                SourceFile(
                    name = file.name,
                    layer = relative.first(),
                    packageName = relative.drop(1).dropLast(1).joinToString("."),
                    imports = IMPORT.findAll(text).map { it.groupValues[1] }.toList(),
                    body = stripCommentsAndStrings(text)
                )
            }
            .toList()
    }

    /**
     * Removes comments and string literals so a rule never trips on prose.
     *
     * This matters: the settings component name deliberately keeps an old package name
     * as a string, and a naive scan would report that as a leftover reference.
     */
    private val BLOCK_COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
    private val LINE_COMMENT = Regex("//[^\n]*")
    private val RAW_STRING = Regex("\"\"\".*?\"\"\"", RegexOption.DOT_MATCHES_ALL)
    private val PLAIN_STRING = Regex("\"(\\\\.|[^\"\\\\])*\"")

    private fun stripCommentsAndStrings(text: String): String = text
        .replace(BLOCK_COMMENT, " ")
        .replace(LINE_COMMENT, " ")
        .replace(RAW_STRING, "\"\"")
        .replace(PLAIN_STRING, "\"\"")
}

internal fun List<SourceFile>.violating(predicate: (String) -> Boolean): List<String> =
    flatMap { source -> source.imports.filter(predicate).map { "${source.name}: $it" } }

internal fun List<SourceFile>.calling(vararg fragments: String): List<String> =
    flatMap { source -> fragments.filter { it in source.body }.map { "${source.name}: $it" } }
