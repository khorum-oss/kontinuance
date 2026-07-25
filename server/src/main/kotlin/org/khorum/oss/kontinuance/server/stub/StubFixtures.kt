package org.khorum.oss.kontinuance.server.stub

import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Fallback fixture JSON for the forward-looking screens. The pipeline / coverage / config endpoints serve
 * these ONLY when their real source is absent — an unknown/old run (no persisted stages), a missing Kover
 * report, or a missing descriptor — so the UI still renders a stable typed shape
 * (see specs/009-web-ui/contracts/stub-api.md). Deploy is now derived from the latest real run
 * ([org.khorum.oss.kontinuance.server.deploy.DeployController]) and no longer has a fixture. Built with the
 * runtime JSON API (no compiler plugin, no new dependency), mirroring
 * [org.khorum.oss.kontinuance.server.JsonView].
 */
@Suppress("MagicNumber") // fixture data: literal progress/coverage/plan values are the point
internal object StubFixtures {

    fun pipeline(runId: String): String = buildJsonObject {
        put("runId", runId)
        putJsonArray("stages") {
            stage("s1", "CHECKOUT") { task("git", "git checkout", "git", "success", 100) }
            stage("s2", "SETUP ENV") {
                task("jdk", "provision jdk 21", "env", "success", 100)
                task("cache", "restore gradle cache", "cache", "success", 100)
            }
            stage("s3", "BUILD") {
                task("core", ":core assemble", "gradle", "success", 100)
                task("api", ":api assemble", "gradle", "success", 100)
                task("legacy", "legacy-adapter package", "maven", "running", 62, "jdk")
            }
            stage("s4", "TEST") {
                task("unit", "unit tests", "gradle", "running", 40, "core", "api")
                task("integ", "integration tests", "gradle", "pending", 0)
                task("lint", "static analysis", "lint", "pending", 0, "git")
            }
            stage("s5", "PUBLISH") { task("pub", "publish → repo manager", "nexus", "pending", 0) }
            stage("s6", "DEPLOY") { task("argo", "argocd sync → stage", "argo", "pending", 0, "pub") }
        }
    }.toString()

    fun coverage(): String = buildJsonObject {
        put("tool", "kover")
        putJsonObject("line") { put("pct", "84.2%"); put("covered", 4821); put("total", 5724) }
        putJsonObject("branch") { put("pct", "72.1%"); put("covered", 611); put("total", 848) }
        put("classes", 142)
        putJsonArray("modules") {
            coverageModule("engine", 91, 84, 214)
            coverageModule("persistence", 88, 79, 46)
            coverageModule("github", 83, 71, 118)
            coverageModule("server", 86, 74, 63)
            coverageModule("dsl", 78, 66, 90)
        }
    }.toString()

    fun config(): String = buildJsonObject {
        put("source", "kontinuance.yml")
        put(
            "text",
            """
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
            """.trimIndent()
        )
        putJsonObject("plan") {
            put("stages", 6)
            put("tasks", 10)
            put("maxParallel", 3)
            put("toolchain", "temurin-21 · gradle 8.8")
            put("publish", "nexus.internal")
            put("deploy", "argocd / kontinuance-stage")
        }
    }.toString()
}

private fun JsonArrayBuilder.stage(id: String, name: String, tasks: JsonArrayBuilder.() -> Unit) =
    addJsonObject {
        put("id", id)
        put("name", name)
        putJsonArray("tasks", tasks)
    }

@Suppress("LongParameterList") // a fixture task is naturally a flat bundle of fields
private fun JsonArrayBuilder.task(
    id: String,
    name: String,
    tool: String,
    status: String,
    progress: Int,
    vararg deps: String,
) = addJsonObject {
    put("id", id)
    put("name", name)
    put("tool", tool)
    put("status", status)
    put("progress", progress)
    putJsonArray("deps") { deps.forEach { add(it) } }
}

private fun JsonArrayBuilder.coverageModule(name: String, linePct: Int, branchPct: Int, missed: Int) =
    addJsonObject {
        put("name", name)
        put("kind", "module")
        put("linePct", linePct)
        put("branchPct", branchPct)
        put("missed", missed)
    }
