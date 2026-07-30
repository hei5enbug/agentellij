package com.agentellij.core.bridge

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Builds the address the embedded browser loads.
 *
 * The five query parameter names are an agreement with the bundled web client under
 * `src/main/resources/webui`, which reads them on start-up. Renaming one here means
 * renaming it there too.
 */
internal object BridgeUiUrl {

    fun build(
        bridgeBaseUrl: String,
        token: String,
        agentApiUrl: String,
        agentName: String,
        pluginVersion: String
    ): String {
        val origin = bridgeBaseUrl.substringBefore("/${BridgeRequest.CONTEXT_PATH}")
        return buildString {
            append("$origin${StaticAssets.URL_PREFIX}/index.html")
            append("?opencodeApi=").append(encode(agentApiUrl))
            append("&ideBridge=").append(encode(bridgeBaseUrl))
            append("&ideBridgeToken=").append(encode(token))
            append("&agentName=").append(encode(agentName))
            append("&v=").append(encode(pluginVersion))
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
