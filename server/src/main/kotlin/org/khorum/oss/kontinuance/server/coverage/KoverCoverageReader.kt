package org.khorum.oss.kontinuance.server.coverage

import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses a Kover coverage report (JaCoCo XML format, e.g. `build/reports/kover/report.xml`) into the
 * `/api/coverage` contract shape (see specs/009-web-ui/contracts/stub-api.md). Modules are derived from
 * the package name segment after `kontinuance` (the repo's module packages are `…kontinuance.<module>`),
 * so the aggregated report yields per-module line/branch coverage. Returns `null` when the report is
 * absent or unparseable, so the controller can fall back to fixture data.
 *
 * No new dependency: the JDK DOM parser is used with external entities disabled (XXE-safe).
 */
object KoverCoverageReader {

    private const val PERCENT = 100.0

    private data class Counter(val missed: Int, val covered: Int) {
        val total: Int get() = missed + covered
        fun pct(): Double = if (total == 0) 0.0 else covered * PERCENT / total
    }

    private class ClassCov(val name: String, val line: Counter, val branch: Counter)

    private class Mod {
        var line = Counter(0, 0)
        var branch = Counter(0, 0)
        val classes = ArrayList<ClassCov>()
        fun add(l: Counter?, b: Counter?) {
            if (l != null) line = Counter(line.missed + l.missed, line.covered + l.covered)
            if (b != null) branch = Counter(branch.missed + b.missed, branch.covered + b.covered)
        }
    }

    fun read(path: Path): String? {
        if (!Files.isRegularFile(path)) return null
        return runCatching { parse(path) }.getOrNull()
    }

    private fun parse(path: Path): String {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isExpandEntityReferences = false
        }
        val report = Files.newInputStream(path).use { factory.newDocumentBuilder().parse(it) }.documentElement

        var totalLine = Counter(0, 0)
        var totalBranch = Counter(0, 0)
        var classes = 0
        val modules = LinkedHashMap<String, Mod>()

        for (pkg in report.children("package")) {
            val moduleName = moduleOf(pkg.getAttribute("name"))
            val mod = modules.getOrPut(moduleName) { Mod() }
            mod.add(pkg.counter("LINE"), pkg.counter("BRANCH"))
            for (cls in pkg.children("class")) {
                classes += 1
                mod.classes.add(
                    ClassCov(
                        name = classDisplayName(cls.getAttribute("name"), moduleName),
                        line = cls.counter("LINE") ?: Counter(0, 0),
                        branch = cls.counter("BRANCH") ?: Counter(0, 0),
                    ),
                )
            }
        }
        report.counter("LINE")?.let { totalLine = it }
        report.counter("BRANCH")?.let { totalBranch = it }

        return buildJsonObject {
            put("tool", "kover")
            metric("line", totalLine)
            metric("branch", totalBranch)
            put("classes", classes)
            putJsonArray("modules") {
                modules.entries.sortedByDescending { it.value.line.total }.forEach { (name, m) ->
                    addModule(name, m)
                }
            }
        }.toString()
    }

    private fun JsonObjectBuilder.metric(key: String, c: Counter) =
        putJsonObject(key) {
            put("pct", fmt(c.pct()))
            put("covered", c.covered)
            put("total", c.total)
        }

    private fun JsonArrayBuilder.addModule(name: String, m: Mod) =
        addJsonObject {
            put("name", name)
            put("kind", "module")
            put("linePct", Math.round(m.line.pct()).toInt())
            put("branchPct", Math.round(m.branch.pct()).toInt())
            put("missed", m.line.missed)
            // Per-class breakdown, worst-covered first (most missed lines), for the UI drilldown (031).
            putJsonArray("classes") {
                m.classes.sortedByDescending { it.line.missed }.forEach { c ->
                    addJsonObject {
                        put("name", c.name)
                        put("linePct", Math.round(c.line.pct()).toInt())
                        put("branchPct", Math.round(c.branch.pct()).toInt())
                        put("missed", c.line.missed)
                    }
                }
            }
        }

    /** A class's display name: the path after `kontinuance/<module>/`, dot-joined (e.g. `model.Step`). */
    private fun classDisplayName(fullName: String, module: String): String {
        val marker = "kontinuance/$module/"
        val i = fullName.indexOf(marker)
        val tail = if (i >= 0) fullName.substring(i + marker.length) else fullName
        return tail.replace('/', '.')
    }

    /** The module segment after `kontinuance` in a JaCoCo package name (`org/khorum/oss/kontinuance/<mod>/…`). */
    private fun moduleOf(pkgName: String): String {
        val parts = pkgName.split('/')
        val i = parts.indexOf("kontinuance")
        return if (i >= 0 && i + 1 < parts.size) parts[i + 1] else parts.firstOrNull().orEmpty().ifEmpty { "root" }
    }

    private fun fmt(pct: Double): String = "%.1f%%".format(pct)

    private fun Element.counter(type: String): Counter? =
        children("counter").firstOrNull { it.getAttribute("type") == type }
            ?.let { Counter(it.getAttribute("missed").toInt(), it.getAttribute("covered").toInt()) }

    /** Direct child elements with the given tag name. */
    private fun Element.children(tag: String): List<Element> {
        val out = ArrayList<Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n.nodeType == Node.ELEMENT_NODE && (n as Element).tagName == tag) out.add(n)
        }
        return out
    }
}
