package org.khorum.oss.kontinuance.engine.dsl.steps

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.engine.dsl.pipeline
import org.khorum.oss.kontinuance.engine.dsl.run
import org.khorum.oss.kontinuance.engine.execution.PipelineEngine
import org.khorum.oss.kontinuance.engine.model.PipelineStatus
import org.khorum.oss.kontinuance.engine.support.CapturingLogSink
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

class MixedStepPipelineTest {

    @Test
    fun `a pipeline mixing a run step and a typed gradle step executes end to end`() = runBlocking {
        assumeTrue(onPath("gradle"), "gradle is not installed; skipping the real-tool cross-type test")

        val pipeline = pipeline {
            name = "mixed"
            stages {
                stage {
                    name = "s"
                    steps {
                        step {
                            name = "shell"
                            run("echo hi")
                        }
                        gradleStep("gradle-version") {
                            tasks("--version")
                            useWrapper = false
                        }
                    }
                }
            }
        }

        val completed = PipelineEngine.default(CapturingLogSink()).run(pipeline)

        assertEquals(PipelineStatus.Success, completed.status)
        assertEquals(
            listOf("shell", "gradle-version"),
            completed.stageRuns[0].stepRuns.map { it.name },
        )
    }

    private fun onPath(binary: String): Boolean {
        val path = System.getenv("PATH") ?: return false
        return path.split(File.pathSeparator).any { dir -> Files.isExecutable(Path.of(dir, binary)) }
    }
}
