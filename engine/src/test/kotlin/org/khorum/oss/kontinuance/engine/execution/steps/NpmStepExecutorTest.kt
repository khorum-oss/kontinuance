package org.khorum.oss.kontinuance.engine.execution.steps

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.model.NpmStep
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.Step
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NpmStepExecutorTest {

    @Test
    fun `script argv runs the named npm script`() {
        assertEquals(listOf("npm", "run", "test"), NpmStepExecutor.argv(NpmStep.script("test")))
    }

    @Test
    fun `install argv maps clean to ci and otherwise to install`() {
        assertEquals(listOf("npm", "ci"), NpmStepExecutor.argv(NpmStep.install(clean = true)))
        assertEquals(listOf("npm", "install"), NpmStepExecutor.argv(NpmStep.install(clean = false)))
    }

    @Test
    fun `a missing npm binary fails the step naming the tool`() = runBlocking {
        val run = NpmStepExecutor().execute(missingToolContext(Step("web", NpmStep.script("test"))))

        val status = run.status
        assertTrue(status is PipelineStatus.Failed, "expected Failed, was $status")
        assertTrue(status.reason.contains("npm"), "reason should name the tool: ${status.reason}")
    }
}
