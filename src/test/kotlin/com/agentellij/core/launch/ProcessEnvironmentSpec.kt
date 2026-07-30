package com.agentellij.core.launch

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ProcessEnvironmentSpec : BehaviorSpec({

    // Stated rather than read from the host, so the spec behaves the same everywhere.
    val unix = ":"
    val windows = ";"

    Given("the search path a launched agent will inherit") {

        When("the package manager directories are missing") {
            Then("they are put in front of what was already there") {
                ProcessEnvironment.pathWithHomebrew("/usr/bin", unix) shouldBe
                    "/opt/homebrew/bin:/usr/local/bin:/usr/bin"
            }

            Then("the host's own separator is used") {
                ProcessEnvironment.pathWithHomebrew("C:\\bin", windows) shouldBe
                    "/opt/homebrew/bin;/usr/local/bin;C:\\bin"
            }
        }

        When("one of them is already present") {
            Then("only the missing one is added") {
                ProcessEnvironment.pathWithHomebrew("/usr/local/bin:/usr/bin", unix) shouldBe
                    "/opt/homebrew/bin:/usr/local/bin:/usr/bin"
            }
        }

        When("all of them are already present") {
            Then("the path is left alone, which is reported as no change") {
                ProcessEnvironment.pathWithHomebrew("/opt/homebrew/bin:/usr/local/bin", unix).shouldBeNull()
            }
        }
    }

    Given("two known defects that are pinned rather than fixed") {

        When("an unrelated directory merely contains a needed one as a substring") {
            Then("the needed directory is wrongly treated as present and is not added") {
                // Matching by substring is what the plugin has always done. Correcting it
                // would change which binary an existing user's agent resolves to.
                ProcessEnvironment.pathWithHomebrew("/usr/local/bin-old:/opt/homebrew/bin", unix)
                    .shouldBeNull()
            }
        }

        When("there is no inherited path at all") {
            Then("a trailing empty entry is produced, which POSIX reads as the working directory") {
                ProcessEnvironment.pathWithHomebrew(null, unix) shouldBe "/opt/homebrew/bin:/usr/local/bin:"
            }
        }
    }
})
