package com.agentellij.ui

import com.intellij.openapi.progress.ProcessCanceledException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class AgentCliInstallRunnerTest {
    @Test
    fun `runner returns exit code and capped output`() {
        val process = FakeProcess(
            output = ByteArray(70 * 1024) { 'x'.code.toByte() },
            exitCode = 7,
            initiallyAlive = false
        )

        val result = AgentCliInstallRunner.run(
            command = listOf("installer"),
            cancellationChecker = NoopCancellationChecker,
            startProcess = { process }
        )

        assertEquals(7, result.exitCode)
        assertEquals(64 * 1024, result.output.length)
    }

    @Test
    fun `runner destroys process when cancelled`() {
        val process = FakeProcess(output = ByteArray(0), exitCode = 0, initiallyAlive = true)

        assertThrows(ProcessCanceledException::class.java) {
            AgentCliInstallRunner.run(
                command = listOf("installer"),
                cancellationChecker = ThrowingCancellationChecker,
                startProcess = { process }
            )
        }
        assertTrue(process.destroyed)
    }

    private object NoopCancellationChecker : CancellationChecker {
        override fun checkCanceled() = Unit
    }

    private object ThrowingCancellationChecker : CancellationChecker {
        override fun checkCanceled() {
            throw ProcessCanceledException()
        }
    }

    private class FakeProcess(
        output: ByteArray,
        private val exitCode: Int,
        initiallyAlive: Boolean
    ) : Process() {
        @Volatile
        private var alive = initiallyAlive

        @Volatile
        var destroyed = false
            private set

        private val input = ByteArrayInputStream(output)

        override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

        override fun getInputStream(): InputStream = input

        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun waitFor(): Int {
            alive = false
            return exitCode
        }

        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
            Thread.sleep(1)
            return !alive
        }

        override fun exitValue(): Int {
            if (alive) throw IllegalThreadStateException()
            return exitCode
        }

        override fun destroy() {
            destroyed = true
            alive = false
        }

        override fun destroyForcibly(): Process {
            destroy()
            return this
        }

        override fun isAlive(): Boolean = alive
    }
}
