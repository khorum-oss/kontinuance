package org.khorum.oss.kontinuance.server

import org.khorum.oss.kontinuance.persistence.FileRunStore
import org.khorum.oss.kontinuance.persistence.RunStore
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
    ): RunStore {
        val dir = storeDir?.let { Path.of(it) }
            ?: Path.of(System.getProperty("user.home"), ".kontinuance", "runs")
        return FileRunStore(dir)
    }

    @Bean
    fun runApi(store: RunStore): RunApi = RunApi(store)
}
