package org.khorum.oss.kontinuance.github.poll

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * Remembers the last head SHA the poller acted on, per tracking key (e.g. a PR). It lets the source
 * emit an event only when a commit is new, and — with a durable backing — resume across restarts
 * without missing or re-processing events.
 *
 * NOTE: this is the placeholder store the 003 plan flags to fold into the future persistence feature.
 */
interface CursorStore {
    /** The last SHA recorded for [key], or `null` if none. */
    fun lastSeen(key: String): String?

    /** Records [sha] as the latest seen for [key]. */
    fun record(key: String, sha: String)
}

/** In-memory [CursorStore] — resets on restart. Used in tests and as a non-durable default. */
class InMemoryCursorStore : CursorStore {
    private val seen = HashMap<String, String>()
    override fun lastSeen(key: String): String? = seen[key]
    override fun record(key: String, sha: String) {
        seen[key] = sha
    }
}

/**
 * File-backed [CursorStore] (a Java properties file) — a minimal **durable placeholder** so the poller
 * resumes from its cursor after downtime. Deliberately simple; the persistence feature will replace it.
 */
class FileCursorStore(private val file: Path) : CursorStore {
    private val props = Properties().apply {
        if (Files.exists(file)) Files.newInputStream(file).use { load(it) }
    }

    override fun lastSeen(key: String): String? = props.getProperty(key)

    override fun record(key: String, sha: String) {
        props.setProperty(key, sha)
        file.parent?.let { Files.createDirectories(it) }
        Files.newOutputStream(file).use { props.store(it, "kontinuance github poll cursors") }
    }
}
