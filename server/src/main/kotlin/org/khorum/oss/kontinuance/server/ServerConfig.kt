package org.khorum.oss.kontinuance.server

import org.khorum.oss.kontinuance.persistence.FileRunLogStore
import org.khorum.oss.kontinuance.persistence.FileRunStore
import org.khorum.oss.kontinuance.persistence.RunLogStore
import org.khorum.oss.kontinuance.persistence.RunStore
import org.khorum.oss.kontinuance.server.projects.ProjectStore
import org.khorum.oss.kontinuance.server.stream.NotifyingRunLogStore
import org.khorum.oss.kontinuance.server.stream.NotifyingRunStore
import org.khorum.oss.kontinuance.server.stream.RunChangeNotifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path

/**
 * Wires the read stack: a [RunStore] over the CI service's persisted run directory and the transport-
 * agnostic [RunApi] over it. The store location comes from the `kontinuance.store` property (bound from
 * the `KONTINUANCE_STORE` env by Spring's relaxed binding), defaulting to `~/.kontinuance/runs` — the
 * same directory the `kontinuance-ci` service writes to. A test can override the `RunStore` bean to
 * point at a seeded/temp store without touching this configuration (FR-007).
 */
@Configuration
class ServerConfig {

    @Bean
    fun runStore(
        @Value("\${kontinuance.store:#{null}}") storeDir: String?,
        notifier: RunChangeNotifier,
    ): RunStore {
        val dir = storeDir?.let { Path.of(it) }
            ?: Path.of(System.getProperty("user.home"), ".kontinuance", "runs")
        // Decorate so every in-process write signals the push streams (025); reads are unaffected.
        return NotifyingRunStore(FileRunStore(dir), notifier)
    }

    @Bean
    fun runApi(store: RunStore): RunApi = RunApi(store)

    /**
     * The per-run output store (018), file-backed under `<kontinuance.store>/logs`. Shares the run store's
     * base directory so a run's record and its log live together. A test can override this bean to point at
     * a seeded/temp store (mirrors the [runStore] convention).
     */
    @Bean
    fun runLogStore(
        @Value("\${kontinuance.store:#{null}}") storeDir: String?,
        notifier: RunChangeNotifier,
    ): RunLogStore {
        val base = storeDir?.let { Path.of(it) }
            ?: Path.of(System.getProperty("user.home"), ".kontinuance", "runs")
        // Decorate so each appended line signals the push log-tail (025); reads are unaffected.
        return NotifyingRunLogStore(FileRunLogStore(base.resolve("logs")), notifier)
    }

    /**
     * The named-descriptor registry (032), file-backed under `<kontinuance.store>/projects`. A test can
     * override this bean to point at a temp directory (mirrors the store conventions).
     */
    @Bean
    fun projectStore(
        @Value("\${kontinuance.store:#{null}}") storeDir: String?,
    ): ProjectStore {
        val base = storeDir?.let { Path.of(it) }
            ?: Path.of(System.getProperty("user.home"), ".kontinuance", "runs")
        return ProjectStore(base.resolve("projects"))
    }
}
