package com.agentellij.bridge

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BridgeCorsPolicyTest {
    @Test
    fun `loopback origins are allowed`() {
        assertTrue(BridgeCorsPolicy.isAllowedOrigin("http://127.0.0.1:3000"))
        assertTrue(BridgeCorsPolicy.isAllowedOrigin("http://localhost:3000"))
        assertTrue(BridgeCorsPolicy.isAllowedOrigin("https://localhost:3000"))
    }

    @Test
    fun `non loopback origins are rejected`() {
        assertFalse(BridgeCorsPolicy.isAllowedOrigin("https://example.com"))
        assertFalse(BridgeCorsPolicy.isAllowedOrigin("file://local"))
        assertFalse(BridgeCorsPolicy.isAllowedOrigin("not a url"))
    }
}
