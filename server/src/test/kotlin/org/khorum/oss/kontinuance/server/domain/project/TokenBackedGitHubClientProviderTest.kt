package org.khorum.oss.kontinuance.server.domain.project

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.khorum.oss.kontinuance.server.store.GitHubTokenStore
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TokenBackedGitHubClientProviderTest {

    @Test
    fun `yields no client when no token can be resolved`(@TempDir dir: Path) {
        val tokens = GitHubTokenStore(dir.resolve("token").toString())
        val provider = TokenBackedGitHubClientProvider(tokens, "KONTINUANCE_GITHUB_TOKEN", "https://api.github.com")

        assertNull(provider.client())
    }

    @Test
    fun `yields a client when a token is stored`(@TempDir dir: Path) {
        val file = dir.resolve("token")
        file.writeText("t0k3n")
        val tokens = GitHubTokenStore(file.toString())
        val provider = TokenBackedGitHubClientProvider(tokens, "KONTINUANCE_GITHUB_TOKEN", "https://api.github.com")

        assertNotNull(provider.client())
    }
}
