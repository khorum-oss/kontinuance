package org.khorum.oss.kontinuance.github.report

/**
 * The stable check-context string Kontinuance posts commit statuses under. A branch-protection
 * "required status check" rule matches on this exact name, so it is a **consumer-facing contract**
 * (Constitution I / FR-004): it MUST NOT change without a deliberate, semver-governed decision —
 * changing it silently breaks every repo whose ruleset requires it.
 */
const val KONTINUANCE_CHECK_CONTEXT: String = "kontinuance/ci"
