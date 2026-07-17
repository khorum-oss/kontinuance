package org.khorum.oss.kontinuance.persistence

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunStagesTest {

    @Test
    fun `round-trips the stage breakdown through toJson and fromJson`() {
        val record = RunRecord(
            id = "run-1",
            pipeline = "demo",
            status = "Success",
            stages = listOf(
                StageRecord(
                    name = "build",
                    status = "Success",
                    steps = listOf(
                        StepRecord(
                            name = "assemble",
                            status = "Success",
                            tool = "gradle",
                            startedAt = Instant.parse("2026-07-17T00:00:00Z"),
                            endedAt = Instant.parse("2026-07-17T00:01:00Z"),
                        ),
                    ),
                ),
                StageRecord(name = "test", status = "Running", steps = listOf(StepRecord("unit", "Running", "gradle"))),
            ),
        )

        val back = RunRecord.fromJson(record.toJson())
        assertEquals(record.stages, back.stages)
    }

    @Test
    fun `older records without a stages array parse to an empty list`() {
        val json = """{"id":"run-1","pipeline":"demo","status":"Success"}"""
        assertTrue(RunRecord.fromJson(json).stages.isEmpty())
    }
}
