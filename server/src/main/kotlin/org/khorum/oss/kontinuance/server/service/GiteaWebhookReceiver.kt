package org.khorum.oss.kontinuance.server.service

import org.khorum.oss.kontinuance.github.trigger.RepositoryBinding
import org.khorum.oss.kontinuance.server.domain.ci.CiBindings
import org.khorum.oss.kontinuance.server.domain.ci.CiDispatchRequest
import org.khorum.oss.kontinuance.server.domain.ci.CiEvent
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

/**
 * Turns one **already-authenticated** Gitea delivery into the `(repo, sha, event)` that [CiDispatcher]
 * takes (043).
 *
 * It owns no run path of its own. The allow-list, the sha pin and the in-flight de-duplication are what
 * make a dispatch safe, and they stay in the dispatcher; this class only decides *whether* a delivery is
 * one of the two things worth building, and which commit it names.
 *
 * Anything else is [Outcome.Ignored], not an error: Gitea sends every subscribed event, and most of them
 * (a PR being labelled, a push to a feature branch) are simply not a reason to build.
 */
@Component
class GiteaWebhookReceiver(
    private val dispatcher: CiDispatcher,
    private val bindings: CiBindings,
    private val heads: GiteaApi,
    private val mapper: ObjectMapper,
) {

    suspend fun receive(event: String?, body: ByteArray): Outcome {
        val payload = runCatching { mapper.readTree(body) }.getOrNull()
            ?: return Outcome.Refused("the delivery body is not JSON")
        val repo = payload.path("repository").text("full_name")
            ?: return Outcome.Refused("the delivery names no repository")
        val configured = bindings.load().getOrElse {
            return Outcome.Refused("dispatch allow-list unavailable: ${it.message}")
        }
        val binding = configured.firstOrNull { it.repo.slug.equals(repo, ignoreCase = true) }
            ?: return Outcome.Refused("repository '$repo' is not configured for dispatch")

        return when (event) {
            "pull_request" -> pullRequest(payload, binding)
            "push" -> push(payload, binding)
            else -> Outcome.Ignored("event '${event.orEmpty()}' does not start a run")
        }
    }

    private suspend fun pullRequest(payload: JsonNode, binding: RepositoryBinding): Outcome {
        val action = payload.text("action")
        if (action !in BUILDING_ACTIONS) return Outcome.Ignored("pull request action '${action.orEmpty()}' does not start a run")

        // The head commit is checked out and built on this host. A head that lives in another repository
        // — or one whose repository is not stated — is unknown provenance, and that fails closed.
        val head = payload.path("pull_request").path("head")
        val headRepo = head.path("repo").text("full_name")
        if (headRepo == null || !headRepo.equals(binding.repo.slug, ignoreCase = true)) {
            return Outcome.Ignored("pull request head is not in ${binding.repo.slug}; forks are not built")
        }
        val sha = head.text("sha") ?: return Outcome.Refused("the pull request names no head commit")
        return dispatch(binding, sha, CiEvent.PR)
    }

    private suspend fun push(payload: JsonNode, binding: RepositoryBinding): Outcome {
        val ref = payload.text("ref")
        if (ref != "refs/heads/${binding.trackedBranch}") {
            return Outcome.Ignored("push to '${ref.orEmpty()}' is not the tracked branch")
        }
        val sha = payload.text("after") ?: return Outcome.Refused("the push names no commit")
        if (sha.all { it == '0' }) return Outcome.Ignored("the tracked branch was deleted; nothing to deliver")

        // A signature has no timestamp, so this delivery may be an old one re-sent. Delivery runs only for
        // the commit the branch points at *now*; anything else has nothing to deliver either way.
        val current = heads.head(binding.repo, binding.trackedBranch).getOrElse {
            return Outcome.Refused("cannot confirm $sha is the head of ${binding.trackedBranch}: ${it.message}")
        }
        if (current != sha) {
            return Outcome.Ignored("$sha is no longer the head of ${binding.trackedBranch}; nothing to deliver")
        }
        return dispatch(binding, sha, CiEvent.PUSH)
    }

    private suspend fun dispatch(binding: RepositoryBinding, sha: String, event: CiEvent): Outcome =
        when (val result = dispatcher.dispatch(CiDispatchRequest(repo = binding.repo.slug, sha = sha, event = event), triggerFor(event))) {
            is CiDispatcher.Result.Accepted -> Outcome.Started(result.id)
            is CiDispatcher.Result.Existing -> Outcome.AlreadyRunning(result.id)
            is CiDispatcher.Result.Rejected -> Outcome.Refused(result.reason)
        }

    /** A string field, or null when it is absent or not a string — never the literal text "null". */
    private fun JsonNode.text(field: String): String? =
        path(field).takeIf { it.isString }?.stringValue()?.takeIf { it.isNotBlank() }

    sealed interface Outcome {
        data class Started(val id: String) : Outcome
        data class AlreadyRunning(val id: String) : Outcome
        /** A well-formed delivery that is not a reason to build. */
        data class Ignored(val reason: String) : Outcome
        /** A delivery that should have built and could not; [reason] is shown in Gitea's delivery log. */
        data class Refused(val reason: String) : Outcome
    }

    companion object {
        /** How a webhook-started gate run is recorded — and how [GiteaStatusReporter] recognises one. */
        const val PR_TRIGGER = "gitea-pr"

        /** How a webhook-started delivery run is recorded. */
        const val PUSH_TRIGGER = "gitea-push"

        private fun triggerFor(event: CiEvent): String = if (event == CiEvent.PUSH) PUSH_TRIGGER else PR_TRIGGER

        // Gitea spells a new commit on an open PR "synchronized"; "synchronize" is GitHub's spelling,
        // accepted so a difference between versions cannot silently stop PRs being re-checked.
        private val BUILDING_ACTIONS = setOf("opened", "reopened", "synchronized", "synchronize")
    }
}
