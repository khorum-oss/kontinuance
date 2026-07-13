package org.khorum.oss.kontinuance.dsl.secret

/**
 * v0 [SecretSource] backed by environment variables.
 *
 * @param env the backing environment map; defaults to the process environment but is
 *   injectable so tests can supply a deterministic map without mutating the real environment.
 */
class EnvSecretSource(
    private val env: Map<String, String> = System.getenv(),
) : SecretSource {
    override fun resolve(name: String): String? = env[name]
}
