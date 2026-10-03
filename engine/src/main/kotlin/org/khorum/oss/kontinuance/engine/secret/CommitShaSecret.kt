package org.khorum.oss.kontinuance.engine.secret

/**
 * The secret name a descriptor uses for the immutable commit the run is building.
 *
 * A secret rather than an ambient environment variable, so a step has to *declare* `secrets:
 * [KONTINUANCE_SHA]` to see it. The engine validates declared secrets before the first step runs, which
 * turns "the trigger forgot to say which commit this is" into a refusal up front instead of an image
 * tagged with an empty string.
 */
const val KONTINUANCE_SHA = "KONTINUANCE_SHA"

/**
 * This source, with [sha] answering [KONTINUANCE_SHA] and everything else falling through unchanged.
 *
 * Lives here rather than beside one trigger because **every** path that starts a run for a known commit
 * owes the pipeline this value — the GitHub poll loop, the dashboard trigger, and an external CI dispatch
 * alike. When only the poll loop applied it, a dispatched delivery run failed engine validation instantly
 * (`required secret 'KONTINUANCE_SHA' could not be resolved`) while the identical descriptor run by the
 * poller worked, and nothing in either module read as if it were missing something.
 */
fun SecretSource.withCommitSha(sha: String): SecretSource =
    SecretSource { name -> if (name == KONTINUANCE_SHA) sha else resolve(name) }
