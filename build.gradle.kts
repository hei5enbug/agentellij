plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.13.1"
    kotlin("jvm") version "2.1.20"
}

group = "com.agentellij"
version = "0.3.2"

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

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.0")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("251")
        }
        description.set("""
            <p>Embed AI coding agents directly into IntelliJ IDEA.
            Chat with your agent in a native tool window while it reads, writes, and navigates your codebase in real time.
            Designed to work with <a href="https://github.com/sst/opencode">OpenCode</a> and other terminal-based AI agents.</p>

            <h3>Key Features</h3>
            <ul>
              <li><b>Embedded Chat UI</b> — JCEF-based web interface inside the IDE</li>
              <li><b>Real-Time Sync</b> — Open files and selections pushed to the agent automatically</li>
              <li><b>Context Shortcuts</b> — Use Ctrl+Shift+I / ⌘⇧I to add the current file, selected lines, or project tree selection based on focus, or use right-click actions</li>
              <li><b>Drag &amp; Drop</b> — Drop files from project tree into chat</li>
              <li><b>Per-Project Sessions</b> — Isolated, token-secured sessions</li>
              <li><b>Background Process</b> — Agent runs in a hidden terminal tab</li>
            </ul>

            <h3>Getting Started</h3>
            <ol>
              <li>Install an AI coding agent (e.g., <a href="https://github.com/sst/opencode">OpenCode</a>)</li>
              <li>Open the AgentellIJ tool window from the right sidebar</li>
              <li>The plugin launches the agent and loads the chat UI automatically</li>
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
