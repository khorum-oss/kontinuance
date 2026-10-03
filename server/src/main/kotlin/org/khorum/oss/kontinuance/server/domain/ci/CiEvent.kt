package org.khorum.oss.kontinuance.server.domain.ci

import com.fasterxml.jackson.annotation.JsonCreator

/**
 * The kind of GitHub event a dispatch is standing in for, and so which of the repository's **configured**
 * descriptors to run: [PR] the gating check, [PUSH] the delivery pipeline.
 *
 * An event kind rather than a pipeline name, deliberately. `POST /api/ci/dispatch` is reachable from the
 * internet through Cloudflare Access with a service token; a caller-supplied path would let whoever holds
 * that token run any descriptor on the delivery host with that host's credentials. A kind is an index into
 * the server's own allow-list, so the set of things a dispatch can run stays exactly the set an operator
 * wrote into the binding config.
 */
enum class CiEvent {
    /** A pull-request check: the repository's `prPipeline`. The default when a request names no event. */
    PR,

    /** A push to the tracked branch: the repository's `pushPipeline`. */
    PUSH,

    ;

    companion object {

        /**
         * Parses the wire value, case-insensitively; returns null for anything unrecognised.
         *
         * Null rather than throwing, because a decoding failure escapes as a framework 400 whose body says
         * nothing about pipelines — while null falls back to [PR], which is the gate. An unknown kind
         * therefore degrades to running the check that was already safe to run, and never to delivering.
         */
        @JvmStatic
        @JsonCreator
        fun from(value: String?): CiEvent? = entries.firstOrNull { it.name.equals(value?.trim(), true) }
    }
}
