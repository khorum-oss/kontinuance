package org.khorum.oss.kontinuance.engine.model

/** Docker's `--pull` policy for a containerized step: when to pull the [Step.image] before running. */
enum class PullPolicy(val flag: String) {
    ALWAYS("always"),
    MISSING("missing"),
    NEVER("never"),
}

/**
 * Per-step container runner options (030), applied only when the step names an [Step.image] (they are
 * `docker run` flags). They deepen the 024 isolation:
 *
 * - [network] → `--network` (e.g. `none` to cut the step off the network, or a named network);
 * - [pull] → `--pull` (image pull policy);
 * - [mapUser] → `-u <hostuid>:<hostgid>` so files the container writes into the mounted workspace are
 *   owned by the host user, not root — otherwise root-owned files break the host-side workspace cleanup.
 *
 * @param network the container network, or `null` for the daemon default. Must be non-blank when set.
 * @param pull when to pull the image, or `null` for the daemon default.
 * @param mapUser run the container as the host user (uid:gid) so workspace files stay host-owned.
 */
data class RunnerOptions(
    val network: String? = null,
    val pull: PullPolicy? = null,
    val mapUser: Boolean = false,
) {
    init {
        network?.let {
            require(it.isNotBlank()) { "runner network must be non-blank when set" }
        }
    }
}
