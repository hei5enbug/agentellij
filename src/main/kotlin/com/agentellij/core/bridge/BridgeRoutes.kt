package com.agentellij.core.bridge

/**
 * The message types the web client may send.
 *
 * The code is the source of truth for this list; the same names appear in the bundled
 * web client under `src/main/resources/webui/js/core/ide-bridge.js`, so adding or
 * renaming one means changing both sides.
 */
internal object BridgeRoutes {
    const val OPEN_FILE = "openFile"
    const val OPEN_URL = "openUrl"
    const val RELOAD_PATH = "reloadPath"
    const val KV_GET = "kv.get"
    const val KV_UPDATE = "kv.update"
    const val MODEL_GET = "model.get"
    const val MODEL_UPDATE = "model.update"
    const val SETTINGS_GET = "settings.get"
    const val SETTINGS_UPDATE = "settings.update"

    val ALL: Set<String> = setOf(
        OPEN_FILE,
        OPEN_URL,
        RELOAD_PATH,
        KV_GET,
        KV_UPDATE,
        MODEL_GET,
        MODEL_UPDATE,
        SETTINGS_GET,
        SETTINGS_UPDATE
    )

    fun isKnown(type: String?): Boolean = type != null && type in ALL

    fun unknownTypeMessage(type: String?): String = "Unknown type: $type"
}
