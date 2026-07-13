package org.khorum.oss.kontinuance.dsl.common

/**
 * `@DslMarker` applied to the Konstellation-generated pipeline builders, restricting implicit
 * receiver scope so an inner builder cannot accidentally call an outer builder's members.
 *
 * Referenced by the KSP processor via the `dslMarkerClass` argument in this module's build script.
 */
@DslMarker
annotation class KontinuanceDsl
