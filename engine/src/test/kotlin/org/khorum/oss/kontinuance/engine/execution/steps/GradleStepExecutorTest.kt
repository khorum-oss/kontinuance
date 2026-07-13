package org.khorum.oss.kontinuance.engine.execution.steps

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.model.GradleStep
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.Step
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GradleStepExecutorTest {

    @Test
    fun `argv prefers the wrapper and appends tasks then args`() {
        val step = GradleStep(tasks = listOf("build"), args = listOf("-x", "test"))
        assertEquals(
            listOf("./gradlew", "build", "-x", "test"),
            GradleStepExecutor.argv(step, wrapperPresent = true),
        )
    }

    @Test
    fun `argv falls back to system gradle without a wrapper or when opted out`() {
        val step = GradleStep(tasks = listOf("build"), args = listOf("-x", "test"))
        assertEquals(
            listOf("gradle", "build", "-x", "test"),
            GradleStepExecutor.argv(step, wrapperPresent = false),
        )

        val noWrapper = GradleStep(tasks = listOf("build"), useWrapper = false)
        assertEquals(listOf("gradle", "build"), GradleStepExecutor.argv(noWrapper, wrapperPresent = true))
    }

    @Test
    fun `a missing gradle binary fails the step naming the tool`() = runBlocking {
        val run = GradleStepExecutor().execute(missingToolContext(Step("compile", GradleStep(listOf("build")))))

        val status = run.status
        assertTrue(status is PipelineStatus.Failed, "expected Failed, was $status")
        assertTrue(status.reason.contains("gradle"), "reason should name the tool: ${status.reason}")
    }
}
