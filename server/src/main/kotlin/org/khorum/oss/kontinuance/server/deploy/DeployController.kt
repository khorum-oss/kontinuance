package org.khorum.oss.kontinuance.server.deploy

import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.khorum.oss.kontinuance.persistence.RunRecord
import org.khorum.oss.kontinuance.persistence.RunStore
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * `GET /api/deploy` — a **delivery view derived from the latest real run** (022), replacing the former
 * fixture. The promotion flow is a `SOURCE` node plus one node per the run's actual stages, each carrying
 * the stage's real status; the environment panel reports the run's real stage-completion, commit, and
 * state.
 *
 * What Kontinuance does **not** know is stated honestly rather than fabricated: it runs no artifact
 * registry and does not query the cluster/ArgoCD (delivery happens as pipeline *steps*, and sync/health
 * live in ArgoCD out-of-band), so the artifact manifest is empty and the panel says so. With no runs yet,
 * the flow is empty.
 */
@RestController
class DeployController(private val store: RunStore) {

    @GetMapping("/api/deploy")
    fun deploy(): ResponseEntity<ByteArray> {
        val run = store.recent(1).firstOrNull()
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(render(run).toByteArray())
    }

    private fun render(run: RunRecord?): String = buildJsonObject {
        putJsonArray("nodes") {
            if (run != null) {
                node("source", "SOURCE", run.repo ?: run.pipeline, "synced", sourceMeta(run))
                run.stages.forEachIndexed { i, stage ->
                    val steps = if (stage.steps.size == 1) "1 step" else "${stage.steps.size} steps"
                    node("stage-$i", stage.name.uppercase(), steps, state(stage.status), stepMeta(stage))
                }
            }
        }
        // Kontinuance runs no artifact registry (publishing is a pipeline step) — nothing to list here.
        putJsonArray("artifacts") { }
        putJsonObject("environment") {
            val done = run?.stages?.count { it.status.startsWith("Success", ignoreCase = true) } ?: 0
            val total = run?.stages?.size ?: 0
            put("podsReady", if (run == null) "—" else "$done/$total")
            put("syncRevision", run?.sha?.take(SHA_LEN) ?: "—")
            put("health", run?.let { state(it.status).uppercase() } ?: "—")
            put("meta", if (run == null) EMPTY_META else EXTERNAL_META)
        }
    }.toString()

    private fun JsonArrayBuilder.node(id: String, label: String, title: String, status: String, meta: String) =
        addJsonObject {
            put("id", id)
            put("label", label)
            put("title", title)
            put("status", status)
            put("meta", meta)
        }

    private companion object {
        const val SHA_LEN = 7
        const val EXTERNAL_META =
            "Registry & ArgoCD sync are external — not tracked by Kontinuance. This view derives from the " +
                "latest run's real pipeline stages; publish/deploy happen as pipeline steps."
        const val EMPTY_META =
            "No runs yet — trigger a run to see its delivery. Registry & ArgoCD sync are external and not " +
                "tracked by Kontinuance."

        fun sourceMeta(run: RunRecord): String =
            listOfNotNull(
                run.sha?.let { "commit ${it.take(SHA_LEN)}" },
                run.trigger?.let { "trigger $it" },
            ).joinToString("\n").ifEmpty { run.pipeline }

        fun stepMeta(stage: org.khorum.oss.kontinuance.persistence.StageRecord): String =
            stage.steps.joinToString("\n") { "${it.name} — ${state(it.status)}" }.ifEmpty { "no steps" }

        /** Map an engine status to the deploy-state vocabulary the UI colors (synced/progressing/failed/…). */
        fun state(status: String): String = when {
            status.startsWith("Success", ignoreCase = true) -> "synced"
            status.startsWith("Fail", ignoreCase = true) || status.startsWith("Timed", ignoreCase = true) ->
                "failed"
            status.startsWith("Run", ignoreCase = true) -> "progressing"
            status.startsWith("Wait", ignoreCase = true) -> "awaiting"
            status.startsWith("Skip", ignoreCase = true) -> "skipped"
            status.startsWith("Cancel", ignoreCase = true) -> "cancelled"
            else -> "pending"
        }
    }
}
