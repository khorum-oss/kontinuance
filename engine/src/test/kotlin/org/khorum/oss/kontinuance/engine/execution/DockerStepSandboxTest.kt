package org.khorum.oss.kontinuance.engine.execution

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.model.RunStep
import org.khorum.oss.kontinuance.engine.model.SecretRef
import org.khorum.oss.kontinuance.engine.model.Step
import org.khorum.oss.kontinuance.engine.support.CapturingLogSink
import java.nio.file.Files
import kotlin.test.assertEquals
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
}
