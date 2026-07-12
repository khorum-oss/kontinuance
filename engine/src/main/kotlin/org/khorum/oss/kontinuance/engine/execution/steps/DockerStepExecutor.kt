package org.khorum.oss.kontinuance.engine.execution.steps

import org.khorum.oss.kontinuance.engine.execution.ProcessStepExecutor
import org.khorum.oss.kontinuance.engine.model.Step
import org.khorum.oss.kontinuance.engine.model.StepDefinition
import org.khorum.oss.kontinuance.engine.model.DockerMode
import org.khorum.oss.kontinuance.engine.model.DockerStep
import java.nio.file.Path

/**
 * Runs a [DockerStep] by invoking the host `docker` CLI: `docker run …` for [DockerMode.RUN] or
 * `docker build …` for [DockerMode.BUILD]. A missing `docker` binary surfaces as a FAILED step
 * naming `docker` (via the shared [ProcessStepExecutor] launch handling).
 */
class DockerStepExecutor : ProcessStepExecutor() {

    override fun supports(definition: StepDefinition): Boolean = definition is DockerStep

    override fun command(step: Step, workingDir: Path): List<String> = argv(step.definition as DockerStep)

    companion object {
        /** The `docker` argv for [docker], driven by its [DockerStep.mode]. Pure. */
        fun argv(docker: DockerStep): List<String> = when (docker.mode) {
            DockerMode.RUN -> runArgv(docker)
            DockerMode.BUILD -> buildArgv(docker)
        }

        private fun runArgv(docker: DockerStep): List<String> = buildList {
            add("docker")
            add("run")
            docker.env.forEach { (key, value) -> add("-e"); add("$key=$value") }
            docker.volumes.forEach { volume -> add("-v"); add(volume) }
            add(docker.image)
            addAll(docker.command)
        }

        private fun buildArgv(docker: DockerStep): List<String> = buildList {
            add("docker")
            add("build")
            docker.tags.forEach { tag -> add("-t"); add(tag) }
            docker.buildArgs.forEach { (key, value) -> add("--build-arg"); add("$key=$value") }
            docker.dockerfile?.let { add("-f"); add(it) }
            add(docker.context)
        }
    }
}
