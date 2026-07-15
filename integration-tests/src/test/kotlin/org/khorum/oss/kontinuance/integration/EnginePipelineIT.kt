package org.khorum.oss.kontinuance.integration

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.execution.PipelineEngine
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.RunStep
import org.khorum.oss.kontinuance.engine.model.Stage
import org.khorum.oss.kontinuance.engine.model.Step
import kotlin.test.assertEquals

/**
 * Representative cross-module integration test: drives the real [PipelineEngine] end-to-end over a
 * small two-stage shell pipeline through its public API (real `ProcessBuilder` execution), asserting
 * the terminal status and step ordering. Its coverage over `:engine` is aggregated at the root,
 * proving the integration-tests module contributes to the quality gate.
 */
class EnginePipelineIT {

    @Test
    fun `engine runs a two-stage shell pipeline end-to-end to Success`() = runBlocking {
        val pipeline = Pipeline(
            name = "alignment-smoke",
            stages = listOf(
                Stage("build", listOf(Step("compile", RunStep("true")))),
                Stage("verify", listOf(Step("check", RunStep("echo ok")))),
            ),
        )

        val run = PipelineEngine.default().run(pipeline)

        assertEquals(PipelineStatus.Success, run.status)
        assertEquals(
            listOf("compile", "check"),
            run.stageRuns.flatMap { it.stepRuns }.map { it.name },
        )
    }
}
