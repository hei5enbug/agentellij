package com.agentellij.platform.process

import java.io.InputStream

interface BackendProcess {
    val inputStream: InputStream
    fun destroy()
    fun isAlive(): Boolean
    fun stopCapture()
}
