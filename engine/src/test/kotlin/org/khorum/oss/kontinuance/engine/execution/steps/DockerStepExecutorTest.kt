package org.khorum.oss.kontinuance.engine.execution.steps

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.model.DockerStep
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.Step
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DockerStepExecutorTest {

    @Test
    fun `run argv places env and volume flags before the image and command`() {
        val step = DockerStep.run(
            image = "node:20",
            command = listOf("node", "--version"),
            env = mapOf("A" to "b"),
            volumes = listOf("/host:/container"),
        )
        assertEquals(
            listOf("docker", "run", "-e", "A=b", "-v", "/host:/container", "node:20", "node", "--version"),
            DockerStepExecutor.argv(step),
        )
    }

    @Test
    fun `build argv carries tags, build args, dockerfile, then the context`() {
        val step = DockerStep.build(
            context = ".",
            dockerfile = "Dockerfile",
            tags = listOf("myapp:ci"),
            buildArgs = mapOf("K" to "V"),
        )
        assertEquals(
            listOf("docker", "build", "-t", "myapp:ci", "--build-arg", "K=V", "-f", "Dockerfile", "."),
            DockerStepExecutor.argv(step),
        )
    }

    @Test
    fun `a missing docker binary fails the step naming the tool`() = runBlocking {
        val step = Step("smoke", DockerStep.run(image = "node:20", command = listOf("node", "--version")))
        val run = DockerStepExecutor().execute(missingToolContext(step))

        val status = run.status
        assertTrue(status is PipelineStatus.Failed, "expected Failed, was $status")
        assertTrue(status.reason.contains("docker"), "reason should name the tool: ${status.reason}")
    }
}
