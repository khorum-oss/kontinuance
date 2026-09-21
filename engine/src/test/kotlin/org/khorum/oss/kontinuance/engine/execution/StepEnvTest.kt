package org.khorum.oss.kontinuance.engine.execution

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.support.CapturingLogSink
import org.khorum.oss.kontinuance.engine.model.RunStep
import org.khorum.oss.kontinuance.engine.model.SecretRef
import org.khorum.oss.kontinuance.engine.model.Step
import org.khorum.oss.kontinuance.engine.secret.EnvSecretSource
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Step-level `env:` — non-secret configuration that a descriptor needs in a step's environment.
 *
 * Before this existed the only way to get a value into a step was to declare it a secret, which meant
 * ordinary configuration (a checkout path, a registry host) travelled through the secret seam and was
 * **redacted in the logs**. That made the very steps you most need to read — a CD descriptor being
 * dogfooded for the first time — print `cd ***`. `env:` exists so non-secret values stay readable.
 */
class StepEnvTest {

    private fun withWorkspace(block: suspend (java.nio.file.Path) -> Unit) = runBlocking {
        val dir = Files.createTempDirectory("knt-env-test-")
        try {
            block(dir)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `env entries reach the step environment`() = withWorkspace { dir ->
        val sink = CapturingLogSink()
        val runner = StepRunner(
            registry = StepExecutorRegistry(listOf(RunStepExecutor())),
            secrets = EnvSecretSource(emptyMap()),
            logSink = sink,
            workspace = dir,
            baseEnvironment = emptyMap(),
        )

        runner.run(Step("show", RunStep("echo \"dir=\$HUB_DIR\""), env = mapOf("HUB_DIR" to "/srv/hub")))

        assertTrue(sink.text().contains("dir=/srv/hub"), "env not injected: ${sink.text()}")
    }

    /** The whole point of the feature: unlike a secret, an `env` value is NOT redacted. */
    @Test
    fun `env values are not masked in logs`() = withWorkspace { dir ->
        val sink = CapturingLogSink()
        val runner = StepRunner(
            registry = StepExecutorRegistry(listOf(RunStepExecutor())),
            secrets = EnvSecretSource(emptyMap()),
            logSink = sink,
            workspace = dir,
            baseEnvironment = emptyMap(),
        )

        runner.run(Step("show", RunStep("echo \"path=\$HUB_DIR\""), env = mapOf("HUB_DIR" to "/srv/hub")))

        val text = sink.text()
        assertTrue(text.contains("/srv/hub"), "env value should be readable: $text")
        assertFalse(text.contains("***"), "env must not be masked like a secret: $text")
    }

    /**
     * Precedence is a security property, not a preference: a descriptor must not be able to shadow a
     * resolved secret with a plaintext value of its own. Secrets are applied last and win.
     */
    @Test
    fun `a secret wins over an env entry of the same name`() = withWorkspace { dir ->
        val sink = CapturingLogSink()
        val runner = StepRunner(
            registry = StepExecutorRegistry(listOf(RunStepExecutor())),
            secrets = EnvSecretSource(mapOf("TOKEN" to "from-secret")),
            logSink = sink,
            workspace = dir,
            baseEnvironment = emptyMap(),
        )

        runner.run(
            Step(
                "show",
                RunStep("test \"\$TOKEN\" = from-secret"),
                secrets = listOf(SecretRef("TOKEN")),
                env = mapOf("TOKEN" to "from-env"),
            ),
        )

        assertFalse(sink.text().contains("from-env"), "env must not shadow a secret: ${sink.text()}")
    }

    @Test
    fun `env overrides a passthrough variable of the same name`() = withWorkspace { dir ->
        val sink = CapturingLogSink()
        val runner = StepRunner(
            registry = StepExecutorRegistry(listOf(RunStepExecutor())),
            secrets = EnvSecretSource(emptyMap()),
            logSink = sink,
            workspace = dir,
            baseEnvironment = mapOf("LANG" to "from-base"),
        )

        runner.run(Step("show", RunStep("echo \"lang=\$LANG\""), env = mapOf("LANG" to "from-env")))

        assertTrue(sink.text().contains("lang=from-env"), "env should win over the base environment")
    }

    @Test
    fun `a step declares no env by default`() {
        assertEquals(emptyMap<String, String>(), Step("s", RunStep("true")).env)
    }
}
