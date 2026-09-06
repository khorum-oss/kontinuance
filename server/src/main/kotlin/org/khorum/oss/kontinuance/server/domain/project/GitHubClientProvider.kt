package org.khorum.oss.kontinuance.server.domain.project

import org.khorum.oss.kontinuance.github.client.GitHubClient
import org.khorum.oss.kontinuance.github.client.RestGitHubClient
import org.khorum.oss.kontinuance.server.store.GitHubTokenStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Produces a [GitHubClient] authenticated with whatever token the deployment has, or `null` when it has
 * none. Kept as a seam so [DescriptorResolver] never touches token plumbing and stays unit-testable
 * without a real client.
 */
fun interface GitHubClientProvider {
    /** An authenticated client, or `null` when no token is resolvable. */
    fun client(): GitHubClient?
}

/**
 * The production [GitHubClientProvider]: resolves the token through 040's [GitHubTokenStore], which
 * prefers an environment variable named by `tokenEnv` over the stored token. The token is never logged
 * and never leaves this class.
 */
@Component
class TokenBackedGitHubClientProvider(
    private val tokens: GitHubTokenStore,
    @Value("\${kontinuance.github.token-env:KONTINUANCE_GITHUB_TOKEN}") private val tokenEnv: String,
    @Value("\${kontinuance.github.base-url:https://api.github.com}") private val baseUrl: String,
) : GitHubClientProvider {

    override fun client(): GitHubClient? =
        tokens.resolve(tokenEnv)?.let { RestGitHubClient(token = it, baseUrl = baseUrl) }
}
