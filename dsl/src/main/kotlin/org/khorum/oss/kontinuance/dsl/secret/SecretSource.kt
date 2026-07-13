package org.khorum.oss.kontinuance.dsl.secret

/**
 * Resolves a named secret to its sensitive value.
 *
 * The abstraction lets a future backend (e.g. Vault) replace the v0 environment-variable
 * implementation without changing any pipeline definition.
 */
fun interface SecretSource {
    /** Returns the value for [name], or `null` if it cannot be resolved. */
    fun resolve(name: String): String?
}
