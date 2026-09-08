package org.khorum.oss.kontinuance.server.domain

/**
 * Fallback fixture data for the forward-looking screens. The coverage / config endpoints serve these ONLY
 * when their real source is absent — a missing Kover report or a missing descriptor — so the UI still
 * renders a stable typed shape (see specs/009-web-ui/contracts/stub-api.md). Deploy is derived from the
 * latest real run ([org.khorum.oss.kontinuance.server.controller.DeployController]) and has no fixture, and
 * neither does pipeline: it answers for one run and only that run's own recorded stages can describe it.
 * Returns typed DTOs (serialized by Jackson), matching what the real readers produce.
 */
@Suppress("MagicNumber") // fixture data: literal progress/coverage/plan values are the point
internal object StubFixtures {

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
}
