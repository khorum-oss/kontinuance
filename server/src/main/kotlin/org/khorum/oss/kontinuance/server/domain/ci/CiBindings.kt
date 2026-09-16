package org.khorum.oss.kontinuance.server.domain.ci

import org.khorum.oss.kontinuance.github.config.EventSourceConfig
import org.khorum.oss.kontinuance.github.trigger.RepositoryBinding
import java.nio.file.Path
import kotlin.io.path.isRegularFile

/**
 * The repositories a CI dispatch is allowed to run, read from an event-source config file.
 *
 * Deliberately the *same* config the poll loop reads, so the allow-list cannot drift into two tables that
 * disagree — but reading it is all this does. Dispatch does not start a poller; on the delivery host the
 * poll loop stays off (`kontinuance.github.autostart: false`) while this file still governs what may run.
 *
 * Returns a [Result] rather than an empty list on failure: a missing or unparseable config has to be
 * distinguishable from "that repository is not configured", or a typo in the YAML reaches the caller as a
 * deliberate refusal and nobody thinks to look at the file.
 *
 * Re-read on every call. The file is a few lines, a dispatch happens at most once per push, and paying
 * that cost means editing the allow-list takes effect without restarting the server.
 */
class CiBindings(private val config: Path) {

    fun load(): Result<List<RepositoryBinding>> =
        if (!config.isRegularFile()) {
            Result.failure(IllegalStateException("no dispatch config at $config"))
        } else {
            runCatching { EventSourceConfig.load(config).bindings }
        }
}
