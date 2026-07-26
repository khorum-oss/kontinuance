package org.khorum.oss.kontinuance.engine.execution

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.model.PullPolicy
import org.khorum.oss.kontinuance.engine.model.RunStep
import org.khorum.oss.kontinuance.engine.model.RunnerOptions
import org.khorum.oss.kontinuance.engine.model.SecretRef
import org.khorum.oss.kontinuance.engine.model.Step
import org.khorum.oss.kontinuance.engine.support.CapturingLogSink
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DockerStepSandboxTest {

    private val workspace = Files.createTempDirectory("knt-sbx-")

    private fun context(step: Step, environment: Map<String, String> = emptyMap()): StepContext =
        StepContext(step = step, workingDir = workspace, environment = environment, logSink = CapturingLogSink())

    @Test
    fun `a step naming an image wraps the command in docker run with the workspace mounted`() {
        val step = Step(
            name = "build",
            definition = RunStep("./gradlew assemble"),
            secrets = listOf(SecretRef("TOKEN")),
            image = "gradle:8.8-jdk21",
        )
        val baseArgv = listOf("/bin/sh", "-c", "./gradlew assemble")

        val argv = DockerStepSandbox().wrap(baseArgv, context(step, mapOf("TOKEN" to "s3cret")))

        assertEquals(
            listOf(
                "docker", "run", "--rm",
                "-v", "${workspace.toAbsolutePath()}:/workspace",
                "-w", "/workspace",
                "-e", "TOKEN",
                "gradle:8.8-jdk21",
                "/bin/sh", "-c", "./gradlew assemble",
            ),
            argv,
        )
        // The secret value never appears on the argv — only its name is forwarded (docker reads the value).
        assertTrue(argv.none { it.contains("s3cret") }, "secret value leaked onto the argv: $argv")
    }

    @Test
    fun `a step with no image runs on the host unchanged`() {
        val step = Step("plain", RunStep("true"))
        val baseArgv = listOf("/bin/sh", "-c", "true")

        assertEquals(baseArgv, DockerStepSandbox().wrap(baseArgv, context(step)))
    }

    @Test
    fun `the host sandbox is always the identity`() {
        val step = Step("x", RunStep("true"), image = "busybox:latest")
        val baseArgv = listOf("echo", "hi")

        assertEquals(baseArgv, StepSandbox.HOST.wrap(baseArgv, context(step)))
    }

    @Test
    fun `runner options add network, pull, and user flags before the mount`() {
        val step = Step(
            name = "build",
            definition = RunStep("./gradlew assemble"),
            image = "gradle:8.8-jdk21",
            runner = RunnerOptions(network = "none", pull = PullPolicy.MISSING, mapUser = true),
        )
        val baseArgv = listOf("/bin/sh", "-c", "./gradlew assemble")

        val argv = DockerStepSandbox(hostUser = "1000:1000").wrap(baseArgv, context(step))

        assertEquals(
            listOf(
                "docker", "run", "--rm",
                "--network", "none",
                "--pull", "missing",
                "-u", "1000:1000",
                "-v", "${workspace.toAbsolutePath()}:/workspace",
                "-w", "/workspace",
                "gradle:8.8-jdk21",
                "/bin/sh", "-c", "./gradlew assemble",
            ),
            argv,
        )
    }

    @Test
    fun `mapUser is a no-op when the host user cannot be determined`() {
        val step = Step("x", RunStep("true"), image = "busybox", runner = RunnerOptions(mapUser = true))

        val argv = DockerStepSandbox(hostUser = null).wrap(listOf("true"), context(step))

        assertFalse(argv.contains("-u"), "no -u flag without a resolved host user: $argv")
    }

    @Test
    fun `no runner options leave the argv exactly as the bare image wrapper`() {
        val step = Step("x", RunStep("true"), image = "busybox")

        val argv = DockerStepSandbox(hostUser = "1000:1000").wrap(listOf("true"), context(step))

        assertEquals(
            listOf(
                "docker", "run", "--rm",
                "-v", "${workspace.toAbsolutePath()}:/workspace",
                "-w", "/workspace",
                "busybox", "true",
            ),
            argv,
        )
    }
}
