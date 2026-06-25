package org.khorum.oss.kontinuance.engine.dsl

import org.khorum.oss.konstellation.metaDsl.CoreDslBuilder

/**
 * Base interface for the Konstellation-generated pipeline builders.
 *
 * The KSP processor (configured via `dslBuilderClasspath` in this module's build script) generates
 * `*DslBuilder` classes that implement this interface; `build()` returns the assembled model object.
 */
@KontinuanceDsl
interface DslBuilder<T> : CoreDslBuilder<T>
