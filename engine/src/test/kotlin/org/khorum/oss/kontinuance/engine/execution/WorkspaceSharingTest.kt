package org.khorum.oss.kontinuance.engine.execution

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.RunStep
import org.khorum.oss.kontinuance.engine.model.Stage
import org.khorum.oss.kontinuance.engine.model.Step
import org.khorum.oss.kontinuance.engine.support.CapturingLogSink
import kotlin.test.assertEquals

class WorkspaceSharingTest {

    @Test
    fun `a later stage reads a file an earlier stage wrote`() = runBlocking {
        val pipeline = Pipeline(
            "p",
            listOf(
                Stage("produce", listOf(Step("write", RunStep("echo artifact > out.txt")))),
                Stage("consume", listOf(Step("read", RunStep("test -f out.txt")))),
            ),
        )

        val run = PipelineEngine.default(CapturingLogSink()).run(pipeline)

        assertEquals(PipelineStatus.Success, run.status, "a later stage should see the earlier stage's file")
    }

    @Test
    fun `a workingDir sub-path lives inside the shared workspace`() = runBlocking {
        // 'write' runs in sub/ and creates x.txt there; 'read' (at the workspace root) sees sub/x.txt.
        val pipeline = Pipeline(
            "p",
            listOf(
                Stage(
                    "s",
                    listOf(
                        Step("write", RunStep("echo hi > x.txt"), workingDirHint = "sub"),
                        Step("read", RunStep("test -f sub/x.txt")),
                    ),
                ),
            ),
        )

        val run = PipelineEngine.default(CapturingLogSink()).run(pipeline)

        assertEquals(PipelineStatus.Success, run.status)
    }
}
