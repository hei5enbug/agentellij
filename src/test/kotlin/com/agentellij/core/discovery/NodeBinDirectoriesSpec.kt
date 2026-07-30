package com.agentellij.core.discovery

import com.agentellij.fixture.FakeSystemProbe
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

class NodeBinDirectoriesSpec : BehaviorSpec({

    val sep = ":"
    val installCommand = listOf("npm", "install", "-g", "@openai/codex")

    Given("an npm command that a desktop-launched IDE has to run") {

        When("npm sits in the Homebrew bin directory") {
            val resolved = NodeBinDirectories.resolve(
                installCommand,
                FakeSystemProbe(
                    variables = mapOf("PATH" to "/usr/bin"),
                    executables = setOf("/opt/homebrew/bin/npm")
                )
            )

            Then("the program becomes an absolute path and the arguments survive") {
                resolved.command shouldBe listOf("/opt/homebrew/bin/npm", "install", "-g", "@openai/codex")
            }

            Then("the augmented path starts with the directory that was found") {
                resolved.path!! shouldStartWith "/opt/homebrew/bin"
            }
        }

        When("several nvm node versions provide npm") {
            val nodeRoot = "/home/me/.nvm/versions/node"

            Then("the newest version is chosen") {
                NodeBinDirectories.resolve(
                    installCommand,
                    FakeSystemProbe(
                        variables = mapOf("PATH" to "/usr/bin"),
                        directories = mapOf(nodeRoot to listOf("v18.19.0", "v20.11.1", "v16.20.0")),
                        executables = setOf("$nodeRoot/v20.11.1/bin/npm", "$nodeRoot/v18.19.0/bin/npm")
                    )
                ).command.first() shouldBe "$nodeRoot/v20.11.1/bin/npm"
            }
        }

        When("npm was installed through fnm") {
            val versionsRoot = "/opt/fnm/node-versions"

            Then("the newest version under the installation directory is chosen") {
                NodeBinDirectories.resolve(
                    installCommand,
                    FakeSystemProbe(
                        variables = mapOf("FNM_DIR" to "/opt/fnm"),
                        directories = mapOf(versionsRoot to listOf("v18.20.0", "v20.11.1")),
                        executables = setOf("$versionsRoot/v20.11.1/installation/bin/npm")
                    )
                ).command.first() shouldBe "$versionsRoot/v20.11.1/installation/bin/npm"
            }
        }

        When("npm was installed under a custom npm prefix") {
            Then("the prefix bin directory is used") {
                NodeBinDirectories.resolve(
                    listOf("npm", "install", "-g", "opencode-ai"),
                    FakeSystemProbe(
                        variables = mapOf("PATH" to "/usr/bin", "NPM_CONFIG_PREFIX" to "/opt/npm-prefix/"),
                        executables = setOf("/opt/npm-prefix/bin/npm")
                    )
                ).command.first() shouldBe "/opt/npm-prefix/bin/npm"
            }
        }

        When("npm was installed through snap") {
            Then("the snap bin directory is used") {
                NodeBinDirectories.resolve(
                    listOf("npm", "install", "-g", "opencode-ai"),
                    FakeSystemProbe(
                        variables = mapOf("PATH" to "/usr/bin"),
                        executables = setOf("/snap/bin/npm")
                    )
                ).command.first() shouldBe "/snap/bin/npm"
            }
        }

        When("the command already names an explicit executable") {
            Then("it is left exactly as it is") {
                NodeBinDirectories.resolve(
                    listOf("/custom/bin/npm", "install", "-g", "opencode-ai"),
                    FakeSystemProbe(variables = mapOf("PATH" to "/usr/bin"))
                ).command.first() shouldBe "/custom/bin/npm"
            }
        }

        When("npm cannot be found anywhere") {
            val resolved = NodeBinDirectories.resolve(
                listOf("npm", "install", "-g", "opencode-ai"),
                FakeSystemProbe(variables = mapOf("PATH" to "/usr/bin"))
            )

            Then("the bare command is kept") {
                resolved.command shouldBe listOf("npm", "install", "-g", "opencode-ai")
            }

            Then("the path is still augmented so npm could find node itself") {
                val entries = resolved.path!!.split(sep)
                entries shouldContain "/opt/homebrew/bin"
                entries shouldContain "/usr/bin"
            }
        }

        When("a candidate directory is already on the path") {
            val resolved = NodeBinDirectories.resolve(
                listOf("npm", "install", "-g", "opencode-ai"),
                FakeSystemProbe(
                    variables = mapOf("PATH" to "/usr/bin$sep/opt/homebrew/bin"),
                    executables = setOf("/opt/homebrew/bin/npm")
                )
            )
            val entries = resolved.path!!.split(sep)

            Then("the resolved directory is moved to the front") {
                entries.first() shouldBe "/opt/homebrew/bin"
            }

            Then("it is not duplicated and the original entries survive") {
                entries.count { it == "/opt/homebrew/bin" } shouldBe 1
                entries shouldContain "/usr/bin"
            }
        }

        When("the host is windows") {
            val command = listOf("cmd", "/c", "npm", "install", "-g", "@openai/codex")
            val resolved = NodeBinDirectories.resolve(command, FakeSystemProbe(isWindows = true))

            Then("nothing is rewritten because cmd is always found") {
                resolved.command shouldBe command
                resolved.path.shouldBeNull()
            }
        }
    }

    Given("the directories where node tools are usually installed") {

        When("nvm is configured through its environment variable") {
            Then("the version directories are listed newest first") {
                NodeBinDirectories.nvmVersionBinDirs(
                    userHome = "/home/me",
                    env = { if (it == "NVM_DIR") "/opt/nvm" else null },
                    listChildren = { dir ->
                        if (dir == "/opt/nvm/versions/node") listOf("v18.19.0", "v20.11.1") else emptyList()
                    }
                ) shouldBe listOf(
                    "/opt/nvm/versions/node/v20.11.1/bin",
                    "/opt/nvm/versions/node/v18.19.0/bin"
                )
            }
        }

        When("nvm is not configured") {
            Then("the home directory default is used") {
                NodeBinDirectories.nvmVersionBinDirs(
                    userHome = "/home/me",
                    env = { null },
                    listChildren = { dir ->
                        if (dir == "/home/me/.nvm/versions/node") listOf("v20.11.1") else emptyList()
                    }
                ) shouldBe listOf("/home/me/.nvm/versions/node/v20.11.1/bin")
            }
        }

        When("fnm is configured through its environment variable") {
            Then("the version directories are listed newest first under installation bin") {
                NodeBinDirectories.fnmVersionBinDirs(
                    userHome = "/home/me",
                    env = { if (it == "FNM_DIR") "/opt/fnm" else null },
                    listChildren = { dir ->
                        if (dir == "/opt/fnm/node-versions") listOf("v18.20.0", "v20.11.1") else emptyList()
                    }
                ) shouldBe listOf(
                    "/opt/fnm/node-versions/v20.11.1/installation/bin",
                    "/opt/fnm/node-versions/v18.20.0/installation/bin"
                )
            }
        }

        When("no version manager is configured") {
            val dirs = NodeBinDirectories.nodeBinDirs(FakeSystemProbe())

            Then("the well-known system locations are still covered") {
                dirs shouldContain "/home/linuxbrew/.linuxbrew/bin"
                dirs shouldContain "/home/me/.linuxbrew/bin"
                dirs shouldContain "/opt/local/bin"
                dirs shouldContain "/snap/bin"
            }
        }
    }

    Given("an npmrc file that sets a prefix") {

        When("the prefix is a plain path") {
            Then("its bin directory is included") {
                NodeBinDirectories.nodeBinDirs(
                    FakeSystemProbe(files = mapOf("/home/me/.npmrc" to listOf("prefix=/opt/custom")))
                ) shouldContain "/opt/custom/bin"
            }
        }

        When("the file has comments, quotes and a repeated key") {
            val dirs = NodeBinDirectories.nodeBinDirs(
                FakeSystemProbe(
                    files = mapOf(
                        "/home/me/.npmrc" to listOf(
                            "prefix=/early",
                            "; a comment",
                            "prefix=\"\${HOME}/.npm-global\""
                        )
                    )
                )
            )

            Then("the home variable is expanded and quotes are stripped") {
                dirs shouldContain "/home/me/.npm-global/bin"
            }

            Then("the last entry wins over the earlier one") {
                dirs shouldNotContain "/early/bin"
            }
        }

        When("the prefix starts with a tilde") {
            Then("it expands to the home directory") {
                NodeBinDirectories.nodeBinDirs(
                    FakeSystemProbe(files = mapOf("/home/me/.npmrc" to listOf("prefix = ~/node-global")))
                ) shouldContain "/home/me/node-global/bin"
            }
        }

        When("both an environment prefix and an npmrc prefix exist") {
            val dirs = NodeBinDirectories.nodeBinDirs(
                FakeSystemProbe(
                    variables = mapOf("NPM_CONFIG_PREFIX" to "/env-prefix"),
                    files = mapOf("/home/me/.npmrc" to listOf("prefix=/rc-prefix"))
                )
            )

            Then("the environment prefix is searched first") {
                (dirs.indexOf("/env-prefix/bin") in 0 until dirs.indexOf("/rc-prefix/bin")) shouldBe true
            }
        }

        When("the npmrc location is overridden") {
            Then("the override is read instead of the home file") {
                NodeBinDirectories.nodeBinDirs(
                    FakeSystemProbe(
                        variables = mapOf("NPM_CONFIG_USERCONFIG" to "/etc/custom-npmrc"),
                        files = mapOf("/etc/custom-npmrc" to listOf("prefix=/opt/x"))
                    )
                ) shouldContain "/opt/x/bin"
            }
        }
    }
})
