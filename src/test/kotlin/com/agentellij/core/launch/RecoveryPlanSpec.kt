package com.agentellij.core.launch

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class RecoveryPlanSpec : BehaviorSpec({

    Given("a process the operating system refused to start") {

        When("the binary is there but has no executable bit") {
            Then("the bit is granted and the launch retried") {
                RecoveryPlan.decide(
                    binaryPath = "/bin/opencode",
                    exists = true,
                    canExecute = false,
                    alternative = null
                ) shouldBe ProcessRecovery.SetExecutableAndRetry
            }
        }

        When("the binary is missing but another copy was found") {
            Then("the other copy is used") {
                RecoveryPlan.decide(
                    binaryPath = "/old/opencode",
                    exists = false,
                    canExecute = false,
                    alternative = "/usr/local/bin/opencode"
                ) shouldBe ProcessRecovery.RetryWithAlternative("/usr/local/bin/opencode")
            }
        }

        When("the binary is missing and no other copy exists") {
            Then("there is nothing left to try") {
                RecoveryPlan.decide(
                    binaryPath = "/old/opencode",
                    exists = false,
                    canExecute = false,
                    alternative = null
                ) shouldBe ProcessRecovery.GiveUp
            }
        }

        When("the binary is there and already executable") {
            Then("the failure was something else, so there is nothing to recover") {
                RecoveryPlan.decide(
                    binaryPath = "/bin/opencode",
                    exists = true,
                    canExecute = true,
                    alternative = "/usr/local/bin/opencode"
                ) shouldBe ProcessRecovery.GiveUp
            }
        }

        When("there was no command to run in the first place") {
            Then("there is nothing to recover") {
                RecoveryPlan.decide(
                    binaryPath = null,
                    exists = false,
                    canExecute = false,
                    alternative = "/usr/local/bin/opencode"
                ) shouldBe ProcessRecovery.GiveUp
            }
        }
    }
})
