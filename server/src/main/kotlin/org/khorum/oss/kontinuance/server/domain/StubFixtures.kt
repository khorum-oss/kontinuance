package org.khorum.oss.kontinuance.server.domain

/**
 * Fallback fixture data for the forward-looking screens. The pipeline / coverage / config endpoints serve
 * these ONLY when their real source is absent — an unknown/old run (no persisted stages), a missing Kover
 * report, or a missing descriptor — so the UI still renders a stable typed shape
 * (see specs/009-web-ui/contracts/stub-api.md). Deploy is derived from the latest real run
 * ([org.khorum.oss.kontinuance.server.controller.DeployController]) and has no fixture. Returns typed DTOs
 * (serialized by Jackson), matching what the real readers produce.
 */
@Suppress("MagicNumber") // fixture data: literal progress/coverage/plan values are the point
internal object StubFixtures {

    fun pipeline(runId: String): PipelineResponse = PipelineResponse(
        runId = runId,
        stages = listOf(
            PipelineStage("s1", "CHECKOUT", listOf(task("git", "git checkout", "git", "success", 100))),
            PipelineStage(
                "s2", "SETUP ENV",
                listOf(
                    task("jdk", "provision jdk 21", "env", "success", 100),
                    task("cache", "restore gradle cache", "cache", "success", 100),
                ),
            ),
            PipelineStage(
                "s3", "BUILD",
                listOf(
                    task("core", ":core assemble", "gradle", "success", 100),
                    task("api", ":api assemble", "gradle", "success", 100),
                    task("legacy", "legacy-adapter package", "maven", "running", 62, "jdk"),
                ),
            ),
            PipelineStage(
                "s4", "TEST",
                listOf(
                    task("unit", "unit tests", "gradle", "running", 40, "core", "api"),
                    task("integ", "integration tests", "gradle", "pending", 0),
                    task("lint", "static analysis", "lint", "pending", 0, "git"),
                ),
            ),
            PipelineStage("s5", "PUBLISH", listOf(task("pub", "publish → repo manager", "nexus", "pending", 0))),
            PipelineStage("s6", "DEPLOY", listOf(task("argo", "argocd sync → stage", "argo", "pending", 0, "pub"))),
        ),
    )

    fun coverage(): CoverageResponse = CoverageResponse(
        tool = "kover",
        line = CoverageMetric("84.2%", 4821, 5724),
        branch = CoverageMetric("72.1%", 611, 848),
        classes = 142,
        modules = listOf(
            CoverageModule("engine", "module", 91, 84, 214),
            CoverageModule("persistence", "module", 88, 79, 46),
            CoverageModule("github", "module", 83, 71, 118),
            CoverageModule("server", "module", 86, 74, 63),
            CoverageModule("dsl", "module", 78, 66, 90),
        ),
    )

    fun config(): ConfigResponse = ConfigResponse(
        source = "kontinuance.yml",
        text = """
            # kontinuance.yml — pipeline definition
            version: 0.4
            project: kontinuance-service
            toolchain:
              jdk: 21
              gradle: 8.8
            stages:
              - checkout
              - build
              - test
              - publish
              - deploy
        """.trimIndent(),
        plan = PlanSummary(
            stages = 6,
            tasks = 10,
            maxParallel = 3,
            toolchain = "temurin-21 · gradle 8.8",
            publish = "nexus.internal",
            deploy = "argocd / kontinuance-stage",
        ),
    )

    @Suppress("LongParameterList") // a fixture task is naturally a flat bundle of fields
    private fun task(
        id: String,
        name: String,
        tool: String,
        status: String,
        progress: Int,
        vararg deps: String,
    ): PipelineTask = PipelineTask(id, name, tool, status, progress, deps.toList())
}
