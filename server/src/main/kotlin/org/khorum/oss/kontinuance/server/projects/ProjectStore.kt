package org.khorum.oss.kontinuance.server.projects

import java.nio.file.Files
import java.nio.file.Path
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

    fun exists(name: String): Boolean = Files.isRegularFile(fileOf(name))

    /** The descriptor text of [name], or `null` if there is no such project. */
    fun get(name: String): String? = fileOf(name).takeIf { Files.isRegularFile(it) }?.readText()

    /** Stores (or replaces) [name]'s descriptor [text]. */
    fun save(name: String, text: String) {
        fileOf(name).writeText(text)
    }

    /** The active project's name, or `null` when none is set. */
    fun activeName(): String? =
        activeFile().takeIf { Files.isRegularFile(it) }?.readText()?.trim()?.ifEmpty { null }

    /** Marks [name] the active project. */
    fun setActive(name: String) {
        activeFile().writeText(name)
    }

    private fun fileOf(name: String): Path = dir.resolve(name + SUFFIX)

    private fun activeFile(): Path = dir.resolve(ACTIVE)

    companion object {
        const val SUFFIX = ".yml"
        private const val ACTIVE = ".active"

        /** A safe project name: letters, digits, and `. _ -`, 1–64 chars (never a path). */
        private val NAME = Regex("[A-Za-z0-9._-]{1,64}")

        fun isValidName(name: String): Boolean = NAME.matches(name)
    }
}
