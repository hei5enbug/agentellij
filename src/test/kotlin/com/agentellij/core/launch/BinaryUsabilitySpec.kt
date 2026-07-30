package com.agentellij.core.launch

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class BinaryUsabilitySpec : BehaviorSpec({

    fun usable(
        binary: String,
        resolved: String,
        existing: Set<String> = emptySet(),
        executable: Set<String> = emptySet()
    ) = BinaryUsability.isUsable(
        binary = binary,
        resolvedBinary = resolved,
        isAbsolute = { it.startsWith("/") },
        exists = { it in existing },
        canExecute = { it in executable }
    )

    Given("a binary about to be launched") {

        When("the path lookup produced an absolute path") {
            Then("a file that exists and can run is usable") {
                usable(
                    binary = "opencode",
                    resolved = "/usr/local/bin/opencode",
                    existing = setOf("/usr/local/bin/opencode"),
                    executable = setOf("/usr/local/bin/opencode")
                ) shouldBe true
            }

            Then("a file that does not exist is not usable") {
                usable(binary = "opencode", resolved = "/usr/local/bin/opencode") shouldBe false
            }

            Then("a file that cannot be run is not usable") {
                usable(
                    binary = "opencode",
                    resolved = "/usr/local/bin/opencode",
                    existing = setOf("/usr/local/bin/opencode")
                ) shouldBe false
            }
        }

        When("the path lookup could not resolve the name") {
            Then("a bare name is not usable, so the install prompt is shown instead") {
                usable(binary = "opencode", resolved = "opencode") shouldBe false
            }

            Then("an absolute path that was configured directly is still checked") {
                usable(
                    binary = "/opt/agents/opencode",
                    resolved = "/opt/agents/opencode",
                    existing = setOf("/opt/agents/opencode"),
                    executable = setOf("/opt/agents/opencode")
                ) shouldBe true
            }
        }
    }
})
