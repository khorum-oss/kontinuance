package org.khorum.oss.kontinuance.server.stub

import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Fixture JSON for the forward-looking screens (pipeline, deploy, coverage, config). These are STUBS: the
 * engine does not yet record stage/task, deploy, or coverage data, so the endpoints serve a stable typed
 * shape (see specs/009-web-ui/contracts/stub-api.md) that the UI can consume now and that a real source
 * can back later without changing the contract. Built with the runtime JSON API (no compiler plugin, no
 * new dependency), mirroring [org.khorum.oss.kontinuance.server.JsonView].
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

    fun deploy(): String = buildJsonObject {
        putJsonArray("nodes") {
            node("build", "SOURCE", "kontinuance-service", "synced", "commit a3f19c2\nbuilt 1.4.2")
            node("stage", "STAGE", "argocd / kontinuance-stage", "progressing", "sync 1.4.2 → live\nrollout 2/3")
            node("prod", "PROD", "manual promotion gate", "pending", "promotes by digest\nawaiting approval")
        }
        putJsonArray("artifacts") {
            artifact("JAR", "kontinuance-core-1.4.2.jar", "sha256:8c1e42aa", "published")
            artifact("JAR", "kontinuance-api-1.4.2.jar", "sha256:5b90d17c", "published")
            artifact("OCI", "kontinuance:1.4.2", "sha256:8c1e42aa", "pushed")
        }
        putJsonObject("environment") {
            put("podsReady", "2/3")
            put("syncRevision", "1.4.2")
            put("health", "Progressing")
            put("meta", "namespace kontinuance-stage\nargocd auto-sync on")
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

private fun JsonArrayBuilder.node(id: String, label: String, title: String, status: String, meta: String) =
    addJsonObject {
        put("id", id)
        put("label", label)
        put("title", title)
        put("status", status)
        put("meta", meta)
    }

private fun JsonArrayBuilder.artifact(kind: String, name: String, digest: String, state: String) =
    addJsonObject {
        put("kind", kind)
        put("name", name)
        put("digest", digest)
        put("state", state)
    }

private fun JsonArrayBuilder.coverageModule(name: String, linePct: Int, branchPct: Int, missed: Int) =
    addJsonObject {
        put("name", name)
        put("kind", "module")
        put("linePct", linePct)
        put("branchPct", branchPct)
        put("missed", missed)
    }
