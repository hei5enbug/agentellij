package com.agentellij.core.discovery

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class BinaryCandidatesSpec : BehaviorSpec({

    fun candidates(
        binaryName: String = "opencode",
        isWindows: Boolean = false,
        userHome: String = "/home/me",
        env: (String) -> String? = { null },
        nodeBinDirectories: List<String> = emptyList()
    ) = BinaryCandidates.candidatesFor(binaryName, isWindows, userHome, env, nodeBinDirectories)

    Given("the places an agent binary might have been installed") {

        When("no environment variables are set") {
            val directories = candidates().map { it.directory }

            Then("the home directory locations are covered") {
                directories shouldContain "/home/me/bin"
                directories shouldContain "/home/me/.opencode/bin"
            }

            Then("the default codex home is derived from the home directory") {
                directories shouldContain "/home/me/.codex/packages/standalone/current"
            }

            Then("the default nvm directory is derived from the home directory") {
                directories shouldContain "/home/me/.nvm/current/bin"
            }

            Then("the windows-only locations are present, harmless on other systems") {
                directories shouldContain "C:/ProgramData/chocolatey/bin"
            }
        }

        When("install directories are configured through the environment") {
            val directories = candidates(
                env = {
                    when (it) {
                        "OPENCODE_INSTALL_DIR" -> "/opt/opencode"
                        "CODEX_INSTALL_DIR" -> "/opt/codex"
                        "XDG_BIN_DIR" -> "/opt/xdg-bin"
                        else -> null
                    }
                }
            ).map { it.directory }

            Then("they are searched before the home directory locations") {
                directories shouldContainInOrder listOf("/opt/opencode", "/opt/codex", "/opt/xdg-bin", "/home/me/bin")
            }
        }

        When("node tooling directories are supplied") {
            val directories = candidates(nodeBinDirectories = listOf("/opt/node/bin")).map { it.directory }

            Then("they sit between the home locations and the version manager fallbacks") {
                directories shouldContainInOrder listOf("/home/me/bin", "/opt/node/bin", "/home/me/.nvm/current/bin")
            }
        }

        When("the same directory arrives from two sources") {
            val directories = candidates(
                env = { if (it == "OPENCODE_INSTALL_DIR") "/opt/node/bin" else null },
                nodeBinDirectories = listOf("/opt/node/bin")
            ).map { it.directory }

            Then("it is only searched once") {
                directories.count { it == "/opt/node/bin" } shouldBe 1
            }
        }

        When("an environment variable is set to an empty value") {
            Then("no blank directory is searched") {
                candidates(env = { if (it == "XDG_BIN_DIR") "" else null })
                    .none { it.directory.isBlank() } shouldBe true
            }
        }
    }

    Given("the file names to look for inside each directory") {

        When("the host is unix") {
            Then("only the bare name is tried") {
                candidates().filter { it.directory == "/home/me/bin" }.map { it.fileName } shouldBe
                    listOf("opencode")
            }
        }

        When("the host is windows") {
            Then("the executable extensions are tried before the bare name") {
                candidates(isWindows = true).filter { it.directory == "/home/me/bin" }.map { it.fileName } shouldBe
                    listOf("opencode.exe", "opencode.cmd", "opencode.bat", "opencode")
            }
        }

        When("every candidate is listed") {
            val windowsCandidates = candidates(isWindows = true)

            Then("all four names in a directory are tried before the next directory") {
                windowsCandidates.take(4).map { it.directory }.distinct().size shouldBe 1
            }

            Then("the fifth candidate has moved on to the next directory") {
                windowsCandidates[4].directory shouldNotBe windowsCandidates[0].directory
            }
        }
    }
})
