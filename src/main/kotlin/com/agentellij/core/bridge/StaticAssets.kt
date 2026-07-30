package com.agentellij.core.bridge

import java.net.URLDecoder

/**
 * Decides which bundled web client file a `/ui` request refers to, and what to call it.
 *
 * Rejecting a path here is what keeps the request from escaping the bundled asset
 * directory, so the checks run on the decoded form rather than the raw one.
 */
internal object StaticAssets {
    const val URL_PREFIX = "/ui"
    private const val RESOURCE_ROOT = "/webui"
    private const val INDEX = "index.html"

    /**
     * Returns the classpath resource path, or null when the request must be refused.
     *
     * Throws [IllegalArgumentException] for malformed percent-encoding, which is the
     * behaviour the server has always had.
     */
    fun resolveResourcePath(requestPath: String): String? {
        val withoutPrefix = requestPath.removePrefix(URL_PREFIX)
        val relative = when {
            withoutPrefix.isEmpty() || withoutPrefix == "/" -> INDEX
            withoutPrefix.startsWith("/") -> withoutPrefix.substring(1)
            else -> withoutPrefix
        }

        val decoded = URLDecoder.decode(relative, "UTF-8")
        if (decoded.contains("..") || decoded.contains("\\")) return null
        return "$RESOURCE_ROOT/$decoded"
    }

    fun mimeTypeFor(path: String): String = when {
        path.endsWith(".html") -> "text/html; charset=utf-8"
        path.endsWith(".css") -> "text/css; charset=utf-8"
        path.endsWith(".js") -> "application/javascript; charset=utf-8"
        path.endsWith(".json") -> "application/json; charset=utf-8"
        path.endsWith(".svg") -> "image/svg+xml"
        path.endsWith(".png") -> "image/png"
        path.endsWith(".woff2") -> "font/woff2"
        path.endsWith(".woff") -> "font/woff"
        else -> "application/octet-stream"
    }
}
