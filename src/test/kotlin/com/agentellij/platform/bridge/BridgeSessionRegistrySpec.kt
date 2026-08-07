package com.agentellij.platform.bridge

import com.intellij.openapi.project.Project
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.lang.reflect.Proxy

/**
 * The project-bound paths of the registry, which the HTTP spec cannot reach.
 *
 * A project is stood in for by a proxy that answers nothing: the registry only ever uses
 * it as a map key, so identity is all that matters. Without this spec the leak the
 * registry exists to prevent would go unexercised.
 */
private fun fakeProject(name: String): Project = Proxy.newProxyInstance(
    Project::class.java.classLoader,
    arrayOf(Project::class.java)
) { proxy, method, args ->
    when (method.name) {
        // Identity equality, because the registry uses a project as a map key.
        "equals" -> proxy === args?.firstOrNull()
        "hashCode" -> System.identityHashCode(proxy)
        "toString" -> name
        else -> null
    }
} as Project

class BridgeSessionRegistrySpec : BehaviorSpec({

    Given("a session opened for a project") {

        When("it is created") {
            val registry = BridgeSessionRegistry()
            val project = fakeProject("alpha")
            val session = registry.open(project)

            Then("it can be found by its identifier") {
                registry.identity(session.id) shouldBe session
            }

            Then("the project it serves can be found from the session") {
                registry.projectOf(session.id) shouldBeSameInstanceAs project
            }

            Then("the session can be found from the project") {
                registry.sessionIdFor(project) shouldBe session.id
            }

            Then("it carries a token of its own") {
                session.token shouldNotBe session.id
            }

            Then("its agent-notification deduplication state starts empty") {
                session.lastNotificationAt shouldBe emptyMap()
            }
        }
    }

    Given("a project that opens a second session") {

        When("the replacement is opened") {
            val registry = BridgeSessionRegistry()
            val project = fakeProject("alpha")
            val first = registry.open(project)
            val second = registry.open(project)

            Then("the first session is gone, so its token no longer authenticates") {
                registry.identity(first.id).shouldBeNull()
            }

            Then("the project now points at the second session") {
                registry.sessionIdFor(project) shouldBe second.id
            }

            Then("only one session remains") {
                registry.all() shouldHaveSize 1
            }
        }
    }

    Given("a session that is closed after its project moved on") {

        When("the stale session is closed") {
            val registry = BridgeSessionRegistry()
            val project = fakeProject("alpha")
            val stale = registry.open(project)
            val current = registry.open(project)
            registry.close(stale.id)

            Then("the project still points at the live session") {
                registry.sessionIdFor(project) shouldBe current.id
            }

            Then("the live session is untouched") {
                registry.identity(current.id) shouldNotBe null
            }
        }
    }

    Given("sessions belonging to different projects") {

        When("one is closed") {
            val registry = BridgeSessionRegistry()
            val alpha = fakeProject("alpha")
            val beta = fakeProject("beta")
            val alphaSession = registry.open(alpha)
            val betaSession = registry.open(beta)
            registry.close(alphaSession.id)

            Then("the other project keeps its session") {
                registry.sessionIdFor(beta) shouldBe betaSession.id
            }

            Then("the closed project has none") {
                registry.sessionIdFor(alpha).shouldBeNull()
            }
        }

        When("everything is closed at once") {
            val registry = BridgeSessionRegistry()
            val alpha = fakeProject("alpha")
            val beta = fakeProject("beta")
            registry.open(alpha)
            registry.open(beta)
            registry.closeAll()

            Then("no session is left holding a project reference") {
                registry.all().shouldBeEmpty()
                registry.sessionIdFor(alpha).shouldBeNull()
                registry.sessionIdFor(beta).shouldBeNull()
            }
        }
    }

    Given("a session opened without a project, as the HTTP spec does") {

        When("it is created and closed") {
            val registry = BridgeSessionRegistry()
            val session = registry.open(null)

            Then("it authenticates like any other") {
                registry.identity(session.id) shouldBe session
            }

            Then("it is bound to no project") {
                registry.projectOf(session.id).shouldBeNull()
            }

            Then("closing it leaves nothing behind") {
                registry.close(session.id)
                registry.all().shouldBeEmpty()
            }
        }
    }

    Given("an identifier that was never issued") {

        When("it is looked up or closed") {
            val registry = BridgeSessionRegistry()

            Then("nothing is found") {
                registry.identity("made-up").shouldBeNull()
            }

            Then("closing it is harmless") {
                registry.close("made-up")
                registry.all().shouldBeEmpty()
            }
        }
    }
})
