package org.khorum.oss.kontinuance.engine.model

/**
 * The one definition of what a project name may be: letters, digits, and `. _ -`, 1–64 characters.
 *
 * It lives here, beside [Pipeline.project], because that is where a name first enters the system — a
 * descriptor declaring `project:`. Enforcing it at parse time means a malformed name fails loudly, with a
 * line the author can act on, instead of parsing cleanly and then resolving to *no* project at run time,
 * which left a pipeline running normally while its runs grouped under nothing.
 *
 * The server's project registry validates against this same rule rather than restating it: there a name
 * also reaches a filesystem path and a URL path variable, so a second, drifting copy of the rule would be
 * a security-relevant divergence rather than a cosmetic one.
 */
object ProjectName {

    private val PATTERN = Regex("[A-Za-z0-9._-]{1,64}")

    /** True when [name] is usable as a project name — the full string must match, not merely contain. */
    fun isValid(name: String): Boolean = PATTERN.matches(name)

    /** The rule in prose, for error messages that have to tell an author what to do about it. */
    const val DESCRIPTION = "letters, digits, and . _ - (1-64 characters)"
}
