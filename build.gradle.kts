plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.13.1"
    kotlin("jvm") version "2.1.20"
}

group = "com.agentellij"
version = "0.1.0"

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
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.1")

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
        description.set("Integrates AI coding agents into IntelliJ IDEA with embedded chat UI, context shortcuts, and editor synchronization.")
        changeNotes.set("Initial release.")
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
