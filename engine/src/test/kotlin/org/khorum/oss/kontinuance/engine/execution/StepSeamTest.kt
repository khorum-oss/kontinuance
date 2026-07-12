package org.khorum.oss.kontinuance.engine.execution

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.RunStep
import org.khorum.oss.kontinuance.engine.model.StepDefinition
import org.khorum.oss.kontinuance.engine.model.StepRun
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class StepSeamTest {

    private val runExecutor = object : StepExecutor {
        override fun supports(definition: StepDefinition): Boolean = definition is RunStep
        override suspend fun execute(context: StepContext): StepRun =
            StepRun(context.step.name, PipelineStatus.Success)
    }

    /** Stands in for an executor that does not recognise the step type at hand. */
    private val unsupportingExecutor = object : StepExecutor {
        override fun supports(definition: StepDefinition): Boolean = false
        override suspend fun execute(context: StepContext): StepRun =
            error("must never be selected")
    }

    @Test
    fun `registry selects the executor whose supports matches the definition`() {
        val registry = StepExecutorRegistry(listOf(unsupportingExecutor, runExecutor))
        assertSame(runExecutor, registry.executorFor(RunStep("echo hi")))
    }

    @Test
    fun `an unsupported definition is rejected with a clear error`() {
        val registry = StepExecutorRegistry(listOf(unsupportingExecutor))
        val ex = assertFailsWith<UnsupportedStepException> {
            registry.executorFor(RunStep("echo hi"))
        }
        assert(ex.message!!.contains("no executor registered"))
    }

    @Test
    fun `an empty registry is rejected at construction`() {
        assertFailsWith<IllegalArgumentException> { StepExecutorRegistry(emptyList()) }
    }
}
