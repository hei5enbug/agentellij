package io.kotest.provided

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.names.DuplicateTestNameMode
import kotlin.time.Duration.Companion.seconds

/**
 * Kotest looks for this exact fully qualified name first, so the configuration is
 * picked up without any extra wiring.
 *
 * The settings here enforce the F.I.R.S.T. properties that the suite promises:
 * a test that blocks for more than a few seconds is not Fast, a silently ignored
 * test is not Self-validating, and a duplicated name usually means a copy-paste slip.
 */
object ProjectConfig : AbstractProjectConfig() {
    override val timeout = 5.seconds
    override val failOnIgnoredTests = true
    override val duplicateTestNameMode = DuplicateTestNameMode.Error
}
