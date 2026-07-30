package com.agentellij.platform.bridge

import com.intellij.openapi.diagnostic.Logger
import com.agentellij.platform.env.IdeLoggerDiagnostics
import com.agentellij.core.util.closeQuietly
import com.intellij.openapi.project.Project
import com.sun.net.httpserver.HttpExchange
import java.util.Collections
import java.util.UUID

/**
 * Who is connected to the bridge right now.
 *
 * A session's identity — its token and its open event streams — is kept apart from the
 * project it belongs to. That separation exists so the HTTP layer can be exercised
 * without an IDE project, but it also states the truth plainly: authentication has
 * nothing to do with which project a session serves.
 *
 * The three maps must agree at all times or a session leaks, holding its event streams
 * and a project reference open. They are private for that reason: every change goes
 * through the operations below.
 */
internal class BridgeSessionRegistry {

    private val diagnostics = IdeLoggerDiagnostics(Logger.getInstance(BridgeSessionRegistry::class.java))

    private val lock = Any()
    private val identities = mutableMapOf<String, BridgeSession>()
    private val projectsBySession = mutableMapOf<String, Project>()
    private val sessionsByProject = mutableMapOf<Project, String>()

    fun identity(sessionId: String): BridgeSession? = synchronized(lock) { identities[sessionId] }

    fun projectOf(sessionId: String): Project? = synchronized(lock) { projectsBySession[sessionId] }

    fun sessionIdFor(project: Project): String? = synchronized(lock) { sessionsByProject[project] }

    fun all(): Collection<BridgeSession> = synchronized(lock) { identities.values.toList() }

    /**
     * Opens a session, replacing whichever one the project had before.
     *
     * Creation and replacement happen under one lock. Doing them as separate atomic map
     * operations would let two opens for the same project interleave and leave one
     * identity with no project pointing at it, which is exactly the leak this class
     * exists to prevent.
     */
    fun open(project: Project?): BridgeSession {
        val (session, replaced) = synchronized(lock) {
            val previous = project?.let { sessionsByProject[it] }?.let(::detach)

            val opened = BridgeSession(
                id = UUID.randomUUID().toString(),
                token = UUID.randomUUID().toString(),
                sseClients = Collections.synchronizedSet(mutableSetOf())
            )
            identities[opened.id] = opened
            if (project != null) {
                projectsBySession[opened.id] = project
                sessionsByProject[project] = opened.id
            }
            opened to previous
        }

        replaced?.let(::disconnect)
        return session
    }

    fun close(sessionId: String) {
        synchronized(lock) { detach(sessionId) }?.let(::disconnect)
    }

    fun closeAll() {
        val closing = synchronized(lock) {
            val all = identities.values.toList()
            identities.clear()
            projectsBySession.clear()
            sessionsByProject.clear()
            all
        }
        closing.forEach(::disconnect)
    }

    fun clientsOf(sessionId: String): MutableSet<HttpExchange>? = identity(sessionId)?.sseClients

    /** Removes a session from all three maps. Must be called while holding the lock. */
    private fun detach(sessionId: String): BridgeSession? {
        val session = identities.remove(sessionId) ?: return null
        projectsBySession.remove(sessionId)?.let { project ->
            // Only if the project still points here: it may already have a newer session.
            if (sessionsByProject[project] == sessionId) sessionsByProject.remove(project)
        }
        return session
    }

    /** Closes the event streams. Done outside the lock, because closing can block. */
    private fun disconnect(session: BridgeSession) {
        synchronized(session.sseClients) {
            session.sseClients.forEach { it.closeQuietly(diagnostics, "an event stream of a closed session") }
        }
    }
}
