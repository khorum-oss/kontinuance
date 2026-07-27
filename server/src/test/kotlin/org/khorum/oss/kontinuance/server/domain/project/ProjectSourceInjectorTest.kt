package org.khorum.oss.kontinuance.server.domain.project

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.model.GitStep
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.RunStep
import org.khorum.oss.kontinuance.engine.model.Stage
import org.khorum.oss.kontinuance.engine.model.Step
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Unit-tests [ProjectSourceInjector] (033): how a project's source (repo/branch) is applied to a parsed
 * pipeline — override the first `git:` step, synthesize a checkout when there is none, or leave the pipeline
 * untouched when the project has no source.
 */
class ProjectSourceInjectorTest {

    private fun withGit(url: String = "https://example.test/orig", ref: String? = "old") = Pipeline(
        name = "demo",
        stages = listOf(
            Stage("checkout", listOf(Step("co", GitStep(url = url, ref = ref)))),
            Stage("build", listOf(Step("b", RunStep("true")))),
        ),
    )

    private fun noGit() = Pipeline(
        name = "demo",
        stages = listOf(Stage("build", listOf(Step("b", RunStep("true"))))),
    )

    private fun firstGit(pipeline: Pipeline): GitStep =
        pipeline.stages.flatMap { it.steps }.map { it.definition }.filterIsInstance<GitStep>().first()

    @Test
    fun `a null source leaves the pipeline untouched`() {
        val p = withGit()
        assertSame(p, ProjectSourceInjector.apply(p, null))
    }

    @Test
    fun `a source without a repo leaves the pipeline untouched`() {
        val p = withGit()
        assertSame(p, ProjectSourceInjector.apply(p, ProjectSource(repo = "  ", branch = "main")))
    }

    @Test
    fun `overrides the first git step's url and ref when a branch is set`() {
        val out = ProjectSourceInjector.apply(withGit(), ProjectSource("https://example.test/new", "release"))
        val git = firstGit(out)
        assertEquals("https://example.test/new", git.url)
        assertEquals("release", git.ref)
    }

    @Test
    fun `overrides only the url and keeps the descriptor ref when no branch is set`() {
        val out = ProjectSourceInjector.apply(withGit(ref = "keep-me"), ProjectSource("https://example.test/new"))
        val git = firstGit(out)
        assertEquals("https://example.test/new", git.url)
        assertEquals("keep-me", git.ref)
    }

    @Test
    fun `synthesizes a prepended checkout stage when the pipeline has no git step`() {
        val out = ProjectSourceInjector.apply(noGit(), ProjectSource("https://example.test/new", "main"))
        assertEquals(listOf("checkout", "build"), out.stages.map { it.name })
        val git = out.stages.first().steps.first().definition as GitStep
        assertEquals("https://example.test/new", git.url)
        assertEquals("main", git.ref)
    }

    @Test
    fun `gives the synthesized checkout stage a non-colliding name`() {
        // A pipeline whose only stage is already named "checkout" but has no git step.
        val p = Pipeline("demo", listOf(Stage("checkout", listOf(Step("noop", RunStep("true"))))))
        val out = ProjectSourceInjector.apply(p, ProjectSource("https://example.test/new"))
        assertEquals(listOf("checkout-2", "checkout"), out.stages.map { it.name })
        assertNull((out.stages[1].steps.first().definition as? GitStep))
    }

    @Test
    fun `a source value that is a commit sha pins the first git step's sha and clears its ref`() {
        val out = ProjectSourceInjector.apply(withGit(ref = "main"), ProjectSource("https://example.test/new", "a1b2c3d"))
        val git = firstGit(out)
        assertEquals("https://example.test/new", git.url)
        assertEquals("a1b2c3d", git.sha)
        assertNull(git.ref)
    }

    @Test
    fun `a non-sha source value stays a branch ref and clears any sha`() {
        val base = Pipeline("demo", listOf(Stage("checkout", listOf(Step("co", GitStep(url = "u", sha = "deadbeef"))))))
        val out = ProjectSourceInjector.apply(base, ProjectSource("https://example.test/new", "release-2"))
        val git = firstGit(out)
        assertEquals("release-2", git.ref)
        assertNull(git.sha)
    }

    @Test
    fun `synthesizes a sha-pinned checkout when the value is a commit sha and there is no git step`() {
        val out = ProjectSourceInjector.apply(noGit(), ProjectSource("https://example.test/new", "0123abcd"))
        val git = out.stages.first().steps.first().definition as GitStep
        assertEquals("0123abcd", git.sha)
        assertNull(git.ref)
    }

    @Test
    fun `overrides only the first git step, leaving later checkouts alone`() {
        val p = Pipeline(
            "demo",
            listOf(
                Stage("s1", listOf(Step("a", GitStep(url = "https://example.test/first", ref = "f")))),
                Stage("s2", listOf(Step("b", GitStep(url = "https://example.test/second", ref = "s")))),
            ),
        )
        val out = ProjectSourceInjector.apply(p, ProjectSource("https://example.test/new", "main"))
        val gits = out.stages.flatMap { it.steps }.map { it.definition }.filterIsInstance<GitStep>()
        assertEquals("https://example.test/new", gits[0].url)
        assertEquals("main", gits[0].ref)
        assertEquals("https://example.test/second", gits[1].url)
        assertEquals("s", gits[1].ref)
    }
}
