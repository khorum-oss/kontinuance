package org.khorum.oss.kontinuance.server.store

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Holds the GitHub personal access token the event source authenticates with, when it was supplied through
 * the dashboard rather than the environment (040).
 *
 * The token is the one secret Kontinuance stores at rest, so the trade is explicit:
 *
 * - The file is created with owner-only permissions (`rw-------`) on any POSIX filesystem, and the parent
 *   state directory with `rwx------`. On a filesystem without POSIX permissions the write still succeeds —
 *   the caller is responsible for the directory in that case.
 * - It is written **only** by the connect endpoint and read only when wiring the poller. No read path
 *   returns it, and [org.khorum.oss.kontinuance.server.domain.SourceResponse] has no field it could occupy.
 * - Supplying a token at all is optional. A deployment that would rather keep the secret out of the server's
 *   filesystem names an environment variable instead (`tokenEnv`), which stays the CLI's behavior and takes
 *   precedence when both are present.
 *
 * [resolve] is the lookup the event source's `env` seam is given: the environment first (so an operator's
 * explicit variable always wins over a stale stored token), then the stored token.
 */
@Component
class GitHubTokenStore(
    @Value("\${kontinuance.github.token-file:#{null}}") tokenFile: String?,
) {
    private val path: Path = tokenFile?.let { Path.of(it) }
        ?: Path.of(System.getProperty("user.home"), ".kontinuance", "github-token")

    /** True when a token has been stored through the API. */
    fun isStored(): Boolean = path.isRegularFile()

    /** Writes [token] with owner-only permissions, replacing any previous one. Blank clears it instead. */
    fun store(token: String) {
        if (token.isBlank()) {
            clear()
            return
        }
        path.parent?.let { parent ->
            parent.createDirectories()
            restrict(parent, PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
        }
        path.writeText(token.trim())
        restrict(path, PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
    }

    /** Removes the stored token, if any. */
    fun clear() {
        path.deleteIfExists()
    }

    /**
     * Resolves the token for [envVar]: the environment variable first, then the stored token. Returns null
     * when neither is set, which the caller reports as "not connected" rather than starting a poller that
     * would fail every cycle with a 401.
     */
    fun resolve(envVar: String, env: (String) -> String? = System::getenv): String? =
        env(envVar)?.takeIf { it.isNotBlank() }
            ?: runCatching { path.takeIf { it.isRegularFile() }?.readText()?.trim() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }

    // Best-effort: a non-POSIX filesystem (or one that rejects the change) leaves the default permissions.
    private fun restrict(target: Path, vararg permissions: PosixFilePermission) {
        runCatching { Files.setPosixFilePermissions(target, permissions.toSet()) }
    }
}
