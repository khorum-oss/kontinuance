package org.khorum.oss.kontinuance.server.store

import org.khorum.oss.kontinuance.engine.model.ProjectName
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
 * A small file-backed registry of named projects (032) and which one is active. A project is registered
 * either by a descriptor's text stored at `<dir>/<name>.yml`, or by a `<dir>/<name>.meta.json` source
 * sidecar (033) naming a repo/branch to read `kontinuance.yml` from at trigger time (041) — a project
 * connected with only a repo has the latter but never the former. The active project's name lives in
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

    /**
     * The registered project names, sorted. A name counts as registered by either file — a stored
     * descriptor or a source sidecar — since 041 lets an operator register a project with only a repo,
     * never writing a `.yml` at all. Suffixes are stripped with an exact trailing match, not a split on
     * the first `.`, so a name that itself contains a suffix-like substring (e.g. `foo.meta`) round-trips
     * unmangled.
     */
    fun list(): List<String> {
        val fileNames = Files.list(dir).use { stream -> stream.map { it.name }.toList() }
        return fileNames.mapNotNull { fileName ->
            when {
                fileName.endsWith(SUFFIX) -> fileName.removeSuffix(SUFFIX)
                fileName.endsWith(SOURCE_SUFFIX) -> fileName.removeSuffix(SOURCE_SUFFIX)
                else -> null
            }
        }.distinct().sorted()
    }

    /** True when [name] is registered by either a stored descriptor or a source sidecar. */
    fun exists(name: String): Boolean =
        Files.isRegularFile(resolve(name + SUFFIX)) || Files.isRegularFile(resolve(name + SOURCE_SUFFIX))

    /**
     * The descriptor text of [name], or `null` if it has none. Deliberately narrower than [exists] —
     * unlike list/exists this never falls back to the source sidecar, because [DescriptorResolver] reads
     * a `null` here as "this project is repo-hosted, go fetch its descriptor from GitHub instead."
     */
    fun get(name: String): String? = resolve(name + SUFFIX).takeIf { Files.isRegularFile(it) }?.readText()

    /** Stores (or replaces) [name]'s descriptor [text]. */
    fun save(name: String, text: String) {
        resolve(name + SUFFIX).writeText(text)
    }

    /**
     * Removes [name]'s stored descriptor only, never its source sidecar (041). This is what makes
     * reverting an override clean: a project with a source falls back to being repo-hosted rather than
     * disappearing, because [exists]/[list] still see it via the sidecar.
     */
    fun delete(name: String) {
        resolve(name + SUFFIX).deleteIfExists()
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

        /**
         * A safe project name (never a path). Delegates to the engine's [ProjectName] rather than
         * restating the rule: a name reaching this store also reaches a filesystem path and a URL path
         * variable, so a second copy that drifted from the descriptor's rule would be a security
         * divergence, not a cosmetic one.
         */
        fun isValidName(name: String): Boolean = ProjectName.isValid(name)
    }
}
