package org.khorum.oss.kontinuance.server.service

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.khorum.oss.kontinuance.github.client.RepoRef
import org.khorum.oss.kontinuance.github.trigger.PublishTarget
import org.khorum.oss.kontinuance.github.trigger.RepositoryBinding
import org.khorum.oss.kontinuance.server.service.GitFixture.commit
import org.khorum.oss.kontinuance.server.service.GitFixture.git
import org.khorum.oss.kontinuance.server.service.GitFixture.refs
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [GitPublisher] against real repositories: what reaches the remote, and what never does. */
class GitPublisherTest {

    @TempDir
    lateinit var dir: Path

    private val source by lazy { GitFixture.source(dir.resolve("src"), "khorum-oss", "relikquary") }
    private val remote by lazy { GitFixture.remote(dir.resolve("remote.git")) }
    private val publisher by lazy { GitPublisher(dir.resolve("work").toString(), pushToken = "", fetchToken = "") }

    private val binding by lazy {
        RepositoryBinding(
            repo = RepoRef("khorum-oss", "relikquary"),
            prPipeline = Path.of("/pr.yaml"),
            cloneBase = dir.resolve("src").toString(),
            publish = PublishTarget(remote.toString(), "main"),
        )
    }

    private fun publish(sha: String, to: RepositoryBinding = binding) = runBlocking { publisher.publish(to, sha) }

    private fun head() = git(source, "rev-parse", "HEAD")

    @Test
    fun `the tracked branch's commit reaches the remote, and no other branch does`() {
        git(source, "checkout", "--quiet", "-b", "unmerged")
        commit(source, "private work")
        git(source, "checkout", "--quiet", "main")
        git(source, "tag", "v1")
        val sha = commit(source, "second")

        assertTrue(publish(sha).isSuccess)

        assertEquals(mapOf("refs/heads/main" to sha), refs(remote))
    }

    @Test
    fun `a later merge fast-forwards the remote, and publishing the same commit twice is harmless`() {
        assertTrue(publish(head()).isSuccess)
        val next = commit(source, "second")

        assertTrue(publish(next).isSuccess)
        assertTrue(publish(next).isSuccess)

        assertEquals(mapOf("refs/heads/main" to next), refs(remote))
    }

    @Test
    fun `it can publish to a branch with another name`() {
        val sha = head()

        assertTrue(publish(sha, binding.copy(publish = PublishTarget(remote.toString(), "public"))).isSuccess)

        assertEquals(mapOf("refs/heads/public" to sha), refs(remote))
    }

    @Test
    fun `a remote with a commit the source does not have refuses the push and is left alone`() {
        assertTrue(publish(head()).isSuccess)
        val elsewhere = GitFixture.source(dir.resolve("other"), "someone", "clone")
        git(elsewhere, "pull", "--quiet", remote.toString(), "main", "--allow-unrelated-histories", "--no-rebase")
        val theirs = commit(elsewhere, "pushed straight to the remote")
        git(elsewhere, "push", "--quiet", remote.toString(), "main")

        val result = publish(commit(source, "second"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("refused the push"), "${result.exceptionOrNull()}")
        assertEquals(mapOf("refs/heads/main" to theirs), refs(remote))
    }

    @Test
    fun `a commit that is not on the tracked branch is not pushed`() {
        git(source, "checkout", "--quiet", "-b", "unmerged")
        val private = commit(source, "private work")

        val result = publish(private)

        assertTrue(result.exceptionOrNull()!!.message!!.contains("is not on main"))
        assertEquals(emptyMap(), refs(remote))
    }

    @Test
    fun `anything but a full commit id is refused before git runs`() {
        assertTrue(publish("--mirror").isFailure)
        assertTrue(publish("main").isFailure)
        assertEquals(emptyMap(), refs(remote))
    }

    @Test
    fun `a source that cannot be fetched is a failure with the reason, not an exception`() {
        val result = publish(head(), binding.copy(cloneBase = dir.resolve("nowhere").toString()))

        assertTrue(result.exceptionOrNull()!!.message!!.startsWith("could not fetch main from the source"))
    }

    @Test
    fun `a binding with no publish target publishes nothing`() {
        assertTrue(publish(head(), binding.copy(publish = null)).isFailure)
        assertEquals(emptyMap(), refs(remote))
    }
}
