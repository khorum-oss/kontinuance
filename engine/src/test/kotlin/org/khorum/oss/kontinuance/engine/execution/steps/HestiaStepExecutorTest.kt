package org.khorum.oss.kontinuance.engine.execution.steps

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.model.HestiaStep
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.Step
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HestiaStepExecutorTest {

    @Test
    fun `render maps to zosn render with pass-through args`() {
        assertEquals(
            listOf("zosn", "render", "--env", "stage"),
            HestiaStepExecutor.argv(HestiaStep.render(listOf("--env", "stage"))),
        )
    }

    @Test
    fun `deploy maps to logos deploy`() {
        assertEquals(
            listOf("logos", "deploy", "--env", "prod"),
            HestiaStepExecutor.argv(HestiaStep.deploy(listOf("--env", "prod"))),
        )
    }

    @Test
    fun `uat maps to euri test and works with no args`() {
        assertEquals(listOf("euri", "test"), HestiaStepExecutor.argv(HestiaStep.uat()))
    }

    @Test
    fun `a missing tool binary fails the step naming the tool`() = runBlocking {
        val step = Step("deliver", HestiaStep.deploy(listOf("--env", "prod")))
        val run = HestiaStepExecutor().execute(missingToolContext(step))

        val status = run.status
        assertTrue(status is PipelineStatus.Failed, "expected Failed, was $status")
        assertTrue(status.reason.contains("logos"), "reason should name the tool: ${status.reason}")
    }
}
