package org.khorum.oss.kontinuance.engine.common

/**
 * Marks a declaration as intentionally excluded from Kover coverage verification.
 *
 * Per Constitution Principle III, exemptions are allowed **only** through this annotation
 * and must carry a justification at the use site explaining why coverage is not meaningful
 * (e.g. a thin, side-effecting integration shim exercised only against the real OS boundary).
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY,
)
annotation class ExcludeFromCoverage(val reason: String = "")
