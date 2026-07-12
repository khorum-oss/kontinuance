package org.khorum.oss.kontinuance.engine.model

/**
 * A reference, by name, to a secret that a [Step] needs injected into its environment.
 *
 * The value is never carried on the model; it is resolved on demand through a
 * [org.khorum.oss.kontinuance.engine.secret.SecretSource] and masked in any log output.
 */
data class SecretRef(val name: String) {
    init {
        require(name.isNotBlank()) { "secret reference name must be non-empty" }
    }
}
