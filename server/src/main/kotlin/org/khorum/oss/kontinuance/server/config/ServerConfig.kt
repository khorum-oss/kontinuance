package org.khorum.oss.kontinuance.server.config

import org.khorum.oss.kontinuance.persistence.RunLogStore
import org.khorum.oss.kontinuance.persistence.RunStore
import org.khorum.oss.kontinuance.persistence.RunStores
import org.khorum.oss.kontinuance.server.domain.RunApi
import org.khorum.oss.kontinuance.server.domain.project.DescriptorResolver
import org.khorum.oss.kontinuance.server.domain.project.GitHubClientProvider
import org.khorum.oss.kontinuance.server.service.RunChangeNotifier
import org.khorum.oss.kontinuance.server.store.ProjectStore
import org.khorum.oss.kontinuance.server.store.NotifyingRunLogStore
import org.khorum.oss.kontinuance.server.store.NotifyingRunStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path

/**
 * Wires the read stack: the run history over the CI service's persisted state directory, and the
 * transport-agnostic [RunApi] over it. The location comes from the `kontinuance.store` property (bound
 * from the `KONTINUANCE_STORE` env by Spring's relaxed binding), defaulting to `~/.kontinuance/runs` —
 * the same directory the `kontinuance-ci` service writes to.
 *
 * `kontinuance.store.backend` (`KONTINUANCE_STORE_BACKEND`) picks the durable implementation: `sqlite`,
 * the default, keeps runs and their output in one embedded database under that directory (042), while
 * `file` keeps the original file-per-run layout. Both are opened through [RunStores] rather than named
 * here, so the CLI resolves the same store from the same two variables. An unrecognised name fails
 * startup with a message instead of quietly serving an empty history.
 *
 * A test can override the `RunStore` bean to point at a seeded/temp store without touching this
 * configuration (FR-007).
 */
@Configuration
class ServerConfig {

    /**
     * The opened backend, shared by the two store beans so a run's record and its output always land in
     * the same place. Not itself a store — the beans below decorate it for the push streams (025).
     */
    @Bean
    fun runStores(
        @Value("\${kontinuance.store:#{null}}") storeDir: String?,
        @Value("\${kontinuance.store.backend:#{null}}") backend: String?,
    ): RunStores.Stores = RunStores.open(storeDirectory(storeDir), RunStores.Backend.of(backend))

    @Bean
    fun runStore(stores: RunStores.Stores, notifier: RunChangeNotifier): RunStore =
        // Decorate so every in-process write signals the push streams (025); reads are unaffected.
        NotifyingRunStore(stores.runs, notifier)

    @Bean
    fun runApi(store: RunStore): RunApi = RunApi(store)

    /** The per-run output store (018), from the same backend so a run's record and log live together. */
    @Bean
    fun runLogStore(stores: RunStores.Stores, notifier: RunChangeNotifier): RunLogStore =
        // Decorate so each appended line signals the push log-tail (025); reads are unaffected.
        NotifyingRunLogStore(stores.logs, notifier)

    /**
     * The named-descriptor registry (032), file-backed under `<kontinuance.store>/projects`. Descriptors
     * are YAML documents an operator edits and the engine parses, not rows to query, so they stay files
     * on the same volume rather than moving into the database with the run history.
     */
    @Bean
    fun projectStore(
        @Value("\${kontinuance.store:#{null}}") storeDir: String?,
    ): ProjectStore = ProjectStore(storeDirectory(storeDir).resolve("projects"))

    private fun storeDirectory(configured: String?): Path =
        configured?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
            ?: Path.of(System.getProperty("user.home"), ".kontinuance", "runs")

    /**
     * Descriptor resolution for the manual trigger path (041). Note the two distinct paths: the live
     * descriptor is a file on this server, while `descriptorPath` is a filename looked up *inside* a
     * project's repository.
     */
    @Bean
    fun descriptorResolver(
        projects: ProjectStore,
        clients: GitHubClientProvider,
        @Value("\${kontinuance.config.descriptor:kontinuance.yml}") liveDescriptorPath: String,
        @Value("\${kontinuance.project.descriptorPath:kontinuance.yml}") repoDescriptorPath: String,
    ): DescriptorResolver = DescriptorResolver(
        projects = projects,
        liveDescriptor = Path.of(liveDescriptorPath),
        descriptorPath = repoDescriptorPath,
        clients = clients,
    )
}
