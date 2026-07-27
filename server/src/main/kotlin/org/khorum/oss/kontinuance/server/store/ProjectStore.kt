package org.khorum.oss.kontinuance.server.store

import org.khorum.oss.kontinuance.server.domain.project.ProjectSource
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.collections.get
import kotlin.io.path.deleteIfExists
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * A small file-backed registry of named pipeline descriptors ("projects", 032) and which one is active.
 * Each project is one descriptor's text stored at `<dir>/<name>.yml`; the active project's name lives in
 * `<dir>/.active`. Activating a project writes its text to the server's live descriptor file (done by the
 * controller), so the trigger and Config screen use it. Small on purpose — a database backend can replace
 * this behind the same surface, mirroring the run/log stores.
 *
 * Project names are validated as safe slugs before they ever reach the filesystem, so a name can never
 * escape [dir] (no path traversal).
 */
class ProjectStore(private val dir: Path) {

    init {
        Files.createDirectories(dir)
    }

    /** The registered project names, sorted. */
    fun list(): List<String> =
        Files.list(dir).use { stream ->
            stream.map { it.name }
                .filter { it.endsWith(SUFFIX) }
                .map { it.removeSuffix(SUFFIX) }
                .sorted()
                .toList()
        }

    fun exists(name: String): Boolean = Files.isRegularFile(resolve(name + SUFFIX))

    /** The descriptor text of [name], or `null` if there is no such project. */
    fun get(name: String): String? = resolve(name + SUFFIX).takeIf { Files.isRegularFile(it) }?.readText()

    /** Stores (or replaces) [name]'s descriptor [text]. */
    fun save(name: String, text: String) {
        resolve(name + SUFFIX).writeText(text)
    }

    /** The active project's name, or `null` when none is set. */
    fun activeName(): String? =
        resolve(ACTIVE).takeIf { Files.isRegularFile(it) }?.readText()?.trim()?.ifEmpty { null }

    /** Marks [name] the active project. */
    fun setActive(name: String) {
        resolve(ACTIVE).writeText(name)
    }

    /**
     * [name]'s source (repo/branch, 033), or `null` when it has none. Stored as a `<name>.meta.json`
     * sidecar; a sidecar without a real repo reads back as `null`.
     */
    fun source(name: String): ProjectSource? {
        val file = resolve(name + SOURCE_SUFFIX).takeIf { Files.isRegularFile(it) } ?: return null
        val map = runCatching { JSON.readValue(file.readText(), Map::class.java) }.getOrNull() ?: return null
        val source = ProjectSource(repo = map["repo"] as? String, branch = map["branch"] as? String)
        return source.takeIf { it.hasRepo }
    }

    /** Sets (or, when [source] has no repo, clears) [name]'s source sidecar. */
    fun saveSource(name: String, source: ProjectSource) {
        val file = resolve(name + SOURCE_SUFFIX)
        if (!source.hasRepo) {
            file.deleteIfExists()
            return
        }
        val payload = buildMap {
            put("repo", source.repo)
            source.branch?.takeIf { it.isNotBlank() }?.let { put("branch", it) }
        }
        file.writeText(JSON.writeValueAsString(payload))
    }

    private fun resolve(child: String): Path = dir.resolve(child)

    companion object {
        const val SUFFIX = ".yml"
        private const val SOURCE_SUFFIX = ".meta.json"
        private const val ACTIVE = ".active"

        // The source sidecar is a small internal `{repo, branch?}` file — a plain Jackson mapper (no Kotlin
        // module needed for the map round-trip) keeps the server off kotlinx-serialization.
        private val JSON: JsonMapper = JsonMapper.builder().build()

        /** A safe project name: letters, digits, and `. _ -`, 1–64 chars (never a path). */
        private val NAME = Regex("[A-Za-z0-9._-]{1,64}")

        fun isValidName(name: String): Boolean = NAME.matches(name)
    }
}
