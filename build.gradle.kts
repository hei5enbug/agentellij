plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.13.1"
    kotlin("jvm") version "2.1.20"
}

group = "com.agentellij"
version = "0.2.0"

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
              <li><b>Context Shortcuts</b> — Add files or selected lines via ⌘⇧I or right-click</li>
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
        changeNotes.set("""
            <h3>0.1.1</h3>
            <ul>
              <li>Add marketplace plugin icons (40×40 SVG, light and dark themes)</li>
              <li>Update vendor metadata with valid contact email and repository URL</li>
              <li>Enhance plugin description with feature list and getting started guide</li>
              <li>Add MIT LICENSE file to distribution</li>
            </ul>

            <h3>0.1.0</h3>
            <ul>
              <li>Embedded JCEF-based chat UI with Tokyo Night dark theme</li>
              <li>Real-time file and selection sync with AI agents</li>
              <li>Context shortcuts: add files, lines, or directories (⌘⇧I)</li>
              <li>Drag and drop files from project tree to chat</li>
              <li>Per-project isolated sessions with token authentication</li>
              <li>Configurable agent binary path and CLI arguments</li>
              <li>Background agent process in hidden terminal tab</li>
              <li>Session management with bulk delete</li>
              <li>Streamed response rendering with debounced markdown</li>
              <li>Context window usage display</li>
            </ul>
        """.trimIndent())
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
