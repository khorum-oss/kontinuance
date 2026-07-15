package org.khorum.oss.kontinuance.github.trigger

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.github.client.RepoRef
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TriggerResolverTest {

    private val repo = RepoRef("khorum-oss", "kontinuance")
    private val prPipeline = Path.of("pipelines/pr.yaml")
    private val pushPipeline = Path.of("pipelines/deliver.yaml")
    private val binding = RepositoryBinding(repo, prPipeline, pushPipeline, trackedBranch = "main")
    private val resolver = TriggerResolver(listOf(binding))

    private fun event(kind: TriggerEvent.Kind, ref: String) =
        TriggerEvent(repo, "sha1", kind, ref)

    @Test
    fun `a PR event resolves to the PR pipeline`() {
        assertEquals(prPipeline, resolver.resolve(event(TriggerEvent.Kind.PULL_REQUEST, "feature")))
    }

    @Test
    fun `a manual event resolves to the PR pipeline`() {
        assertEquals(prPipeline, resolver.resolve(event(TriggerEvent.Kind.MANUAL, "feature")))
    }

    @Test
    fun `a push to the tracked branch resolves to the delivery pipeline`() {
        assertEquals(pushPipeline, resolver.resolve(event(TriggerEvent.Kind.PUSH, "main")))
    }

    @Test
    fun `a push to a non-tracked branch resolves to nothing`() {
        assertNull(resolver.resolve(event(TriggerEvent.Kind.PUSH, "develop")))
    }

    @Test
    fun `a push with no delivery pipeline configured resolves to nothing`() {
        val noPush = TriggerResolver(listOf(RepositoryBinding(repo, prPipeline, pushPipeline = null)))
        assertNull(noPush.resolve(event(TriggerEvent.Kind.PUSH, "main")))
    }

    @Test
    fun `an event for an unconfigured repo resolves to nothing`() {
        val other = TriggerEvent(RepoRef("someone", "else"), "sha1", TriggerEvent.Kind.PULL_REQUEST, "x")
        assertNull(resolver.resolve(other))
    }
}
