package com.agentellij.platform.env

/** Reads what kind of machine the plugin is running on. Belongs to the probing layer. */
internal fun currentPlatformIsWindows(): Boolean =
    System.getProperty("os.name").lowercase().contains("win")
