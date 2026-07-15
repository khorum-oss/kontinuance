package org.khorum.oss.kontinuance.persistence

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.model.Pipeline
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.model.Run
import org.khorum.oss.kontinuance.engine.model.RunId
import org.khorum.oss.kontinuance.engine.model.StageRun
import org.khorum.oss.kontinuance.engine.model.StepRun
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RunRecordTest {

    private val now = Instant.parse("2026-07-15T12:00:00Z")

    private fun run(status: PipelineStatus, step: StepRun? = null): Run {
        val steps = listOfNotNull(step)
        return Run(RunId("run-1"), Pipeline("pr-check", emptyList()), status, listOf(StageRun("s", status, steps)))
    }

    @Test
    fun `from a successful run maps status, pipeline, and CI context`() {
        val step = StepRun("ok", PipelineStatus.Success, exitCode = 0, startedAt = now, endedAt = now.plusSeconds(5))
        val record = RunRecord.from(run(PipelineStatus.Success, step), now, repo = "o/r", sha = "abc", trigger = "PULL_REQUEST")

        assertEquals("run-1", record.id)
        assertEquals("pr-check", record.pipeline)
        assertEquals("Success", record.status)
        assertEquals("o/r", record.repo)
        assertEquals("abc", record.sha)
        assertEquals("PULL_REQUEST", record.trigger)
        assertEquals(now, record.startedAt)
        assertEquals(now.plusSeconds(5), record.endedAt)
        assertNull(record.failingStep)
    }

    @Test
    fun `from a failed run carries the failing step and reason`() {
        val record = RunRecord.from(run(PipelineStatus.Failed("compile", "exit 3")), now)

        assertEquals("Failed", record.status)
        assertEquals("compile", record.failingStep)
        assertEquals("exit 3", record.reason)
    }

    @Test
    fun `json round-trips all fields`() {
        val original = RunRecord(
            id = "run-9", pipeline = "deliver", status = "Failed", failingStep = "push", reason = "boom",
            startedAt = now, endedAt = now.plusSeconds(9), repo = "o/r", sha = "def", trigger = "PUSH",
        )
        assertEquals(original, RunRecord.fromJson(original.toJson()))
    }

    @Test
    fun `json omits absent optionals and still round-trips`() {
        val minimal = RunRecord(id = "run-min", pipeline = "p", status = "Success")
        assertEquals(minimal, RunRecord.fromJson(minimal.toJson()))
    }
}
