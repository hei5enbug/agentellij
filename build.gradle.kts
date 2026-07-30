plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.13.1"
    id("org.jetbrains.kotlinx.kover") version "0.9.9"
    kotlin("jvm") version "2.1.20"
}

group = "com.agentellij"
version = "0.4.3"

val kotestVersion = "6.2.3"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.6")

    intellijPlatform {
        intellijIdea("2025.3.4")
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.plugins.terminal")

        pluginVerifier()
        zipSigner()
    }

    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-property:$kotestVersion")
    testImplementation("org.instancio:instancio-core:5.6.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // The IntelliJ platform jars on the test classpath reference JUnit 4 types, and the
    // Gradle test worker fails to start without them. Only the classes are needed: the
    // vintage engine is deliberately absent so no JUnit 4 test is ever discovered.
    testRuntimeOnly("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("251")
        }
        description.set("""
            <p>AgentellIJ hosts terminal-based AI coding agents in IntelliJ IDEA, so you can work with <a href="https://github.com/sst/opencode">OpenCode</a>, Claude Code, Codex CLI, or the native Terminal agent without leaving your project.
            Use an embedded chat window for graphical work or an interactive terminal for terminal workflows.</p>

            <h3>Key Features</h3>
            <ul>
              <li><b>Switch modes</b> — Change between terminal (TUI) and graphical chat (GUI) modes from the tool window toolbar without restarting. Terminal-only agents stay in terminal mode.</li>
              <li><b>Choose your agent</b> — Select OpenCode, Claude Code, Codex CLI, or the native Terminal agent from the toolbar; your selection is remembered.</li>
              <li><b>Per-agent program paths</b> — Keep a separate binary path for each agent, so every profile uses the command you choose.</li>
              <li><b>Consent-based installation</b> — When a command-line agent is missing, review the exact install command and approve it before it runs. The native Terminal agent needs no installation or binary.</li>
              <li><b>Context sharing</b> — Add the current file, selected lines, or project-tree items to your agent with shortcuts or context-menu actions.</li>
              <li><b>Live editor context</b> — Open files and the active editor stay available to the agent automatically as you work.</li>
              <li><b>Drag &amp; Drop</b> — Drop files from the project tree into chat.</li>
            </ul>

            <h3>Getting Started</h3>
            <ol>
              <li>Install a supported command-line agent (for example, <a href="https://github.com/sst/opencode">OpenCode</a>), or use the native Terminal agent.</li>
              <li>Open the AgentellIJ tool window from the right sidebar.</li>
              <li>Choose an agent and switch modes from the toolbar whenever you need.</li>
            </ol>
        """.trimIndent())
        changeNotes.set(provider {
            fileTree("changelogs") {
                include("*.html")
            }.files
                .sortedByDescending { it.nameWithoutExtension }
                .joinToString("\n") { it.readText().trim() }
        })
    }
}

tasks {
    patchPluginXml {
        untilBuild.set("261.*")
    }

    test {
        useJUnitPlatform()
    }

    prepareSandbox {
        from(rootProject.rootDir.resolve("LICENSE")) {
            into(intellijPlatform.projectName.get())
        }
    }
}

// Coverage is measured for the pure layer only. The platform layer is deliberately
// untested, so including it would make the number meaningless.
//
// Three bounds, because each catches a different kind of gap: an untested function, an
// untested line, and an untested branch inside a line that is otherwise covered.
//
// None of them is 100. A handful of defensive branches cannot be reached without
// contriving a failure, and forcing those up would only add tests that assert nothing a
// user would notice.
kover {
    reports {
        filters {
            includes {
                classes("com.agentellij.core.*")
            }
        }
        total {
            verify {
                rule {
                    groupBy = kotlinx.kover.gradle.plugin.dsl.GroupingEntityType.APPLICATION
                    bound {
                        coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE
                        minValue = 85
                    }
                    bound {
                        coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.INSTRUCTION
                        minValue = 90
                    }
                    bound {
                        coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH
                        minValue = 70
                    }
                }
            }
        }
    }
}

intellijPlatform {
    pluginVerification {
        ides {
            recommended()
        }
    }

    signing {
        providers.environmentVariable("CERTIFICATE_CHAIN").orNull?.let {
            certificateChainFile.set(file(it))
        }
        providers.environmentVariable("PRIVATE_KEY").orNull?.let {
            privateKeyFile.set(file(it))
        }
        password.set(providers.environmentVariable("PRIVATE_KEY_PASSWORD"))
    }

    publishing {
        token.set(providers.environmentVariable("PUBLISH_TOKEN"))
    }
}
