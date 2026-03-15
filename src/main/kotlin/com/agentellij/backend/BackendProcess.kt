package com.agentellij.backend

import java.io.InputStream

interface BackendProcess {
    val inputStream: InputStream
    fun destroy()
    fun isAlive(): Boolean
    fun stopCapture()
}
